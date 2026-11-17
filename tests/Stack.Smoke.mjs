// Verificación con escrituras: ejecutar solamente sobre una base aislada y desechable.
import assert from "node:assert/strict";
import { randomBytes, randomUUID, randomInt } from "node:crypto";
import { writeFile } from "node:fs/promises";

if (process.env.STACK_TEST_CONFIRM_ISOLATED !== "yes") throw new Error("Esta comprobación requiere un entorno aislado confirmado.");
const site = new URL(process.env.STACK_TEST_URL || "http://localhost:18081").origin;
const api = site + "/api/v1";
const checks = [];
const check = (condition, description) => { assert.ok(condition, description); checks.push(description); };
const admin = { email: process.env.STACK_ADMIN_EMAIL, password: process.env.STACK_ADMIN_PASSWORD };
if (!admin.email || !admin.password) throw new Error("Faltan las credenciales administrativas de la base aislada.");

function context() {
    const cookies = new Map();
    let csrf;
    async function send(path, { method = "GET", body, token, cookieAuth = false } = {}) {
        const response = await fetch(api + path, {
            method,
            headers: { Origin: site, ...(body ? { "Content-Type": "application/json" } : {}),
                ...(token ? { Authorization: "Bearer " + token } : {}),
                ...(cookieAuth ? { Cookie: [...cookies].map(([key, value]) => key + "=" + value).join("; "), ...(csrf ? { "X-XSRF-TOKEN": csrf } : {}) } : {}) },
            ...(body ? { body: JSON.stringify(body) } : {})
        });
        if (cookieAuth) for (const cookie of response.headers.getSetCookie()) {
            const [pair] = cookie.split(";"); const at = pair.indexOf("="); cookies.set(pair.slice(0, at), pair.slice(at + 1));
        }
        return response;
    }
    async function ok(path, options) {
        const response = await send(path, options);
        assert.ok(response.ok, `${options?.method || "GET"} ${path}: HTTP ${response.status}`);
        return response.status === 204 ? null : response.json();
    }
    async function login(credentials) {
        csrf = (await ok("/auth/csrf", { cookieAuth: true })).token;
        const response = await send("/auth/login", { method: "POST", body: credentials, cookieAuth: true });
        assert.equal(response.status, 200, "Login a través del proxy");
        const session = await response.json();
        check(response.headers.getSetCookie().some(value => value.startsWith("kynex_refresh=") && /HttpOnly/i.test(value) && /SameSite=Strict/i.test(value)), "Cookie HttpOnly y SameSite conservada por el proxy para " + session.user.role);
        return session;
    }
    return { send, ok, login, setCsrf: value => { csrf = value; } };
}

const publicClient = context();
const page = await fetch(site + "/productos/2");
check(page.ok && (await page.text()).includes('id="root"'), "Recarga directa de una ruta React Router");
check((await fetch(site + "/assets/no-existe.js")).status === 404, "Un asset inexistente devuelve 404 en vez del HTML de React");
check((await publicClient.send("/profile")).status === 401, "La API privada no se transforma en una página pública del proxy");
check([401, 403].includes((await publicClient.send("/auth/login", { method: "POST", body: admin })).status), "El proxy rechaza el login sin CSRF aunque las credenciales sean válidas");
const products = await publicClient.ok("/products");
check(Array.isArray(products) && products.length >= 6, "Catálogo persistido accesible desde el contenedor frontend");
const picture = await fetch(site + products[0].image);
check(picture.ok && picture.headers.get("content-type").startsWith("image/"), "Imagen del backend accesible mediante /media");

const administration = context();
const adminSession = await administration.login(admin);
const clientCredentials = { email: "docker-" + randomUUID() + "@kynex.test", password: randomBytes(24).toString("hex") };
const customer = context();
const runDigits = String(randomInt(10000000, 99999999));
const runSum = [...runDigits].reverse().reduce((sum, digit, index) => sum + Number(digit) * (2 + index % 6), 0);
const verifier = 11 - runSum % 11;
const run = runDigits + (verifier === 11 ? "0" : verifier === 10 ? "K" : String(verifier));
customer.setCsrf((await customer.ok("/auth/csrf", { cookieAuth: true })).token);
await customer.ok("/auth/register", { method: "POST", cookieAuth: true, body: { ...clientCredentials, run, firstName: "Prueba", lastName: "Docker", region: "Región Metropolitana de Santiago", commune: "Santiago", address: "Calle de pruebas 123" } });
let session = await customer.login(clientCredentials);
check(session.user.role === "Cliente", "Registro real y login de Cliente a través de Nginx");
check((await customer.send("/admin/users", { token: session.accessToken })).status === 403, "Rol Cliente rechazado en gestión administrativa");
const refreshed = await customer.ok("/auth/refresh", { method: "POST", cookieAuth: true });
check(refreshed.user.id === session.user.id, "Renovación por cookie después de reconstruir el estado de acceso");
session = refreshed;
const auth = { token: session.accessToken };
let cart = await customer.ok("/cart", auth);
cart = await customer.ok("/cart/items/2", { ...auth, method: "PUT", body: { quantity: 2, version: cart.version } });
const before = await publicClient.ok("/products/2");
const order = await customer.ok("/orders", { ...auth, method: "POST", body: { requestId: randomUUID(), version: cart.version, delivery: { recipient: "Prueba Docker", region: "Región Metropolitana de Santiago", commune: "Santiago", address: "Calle de pruebas 123" } } });
const payment = await customer.ok(`/orders/${order.id}/payments`, { ...auth, method: "POST", body: { requestId: randomUUID(), scenario: "APPROVE" } });
const paid = await customer.ok(`/orders/${order.id}/payments/${payment.id}/confirm`, { ...auth, method: "POST", body: {} });
await customer.ok(`/orders/${order.id}/payments/${payment.id}/confirm`, { ...auth, method: "POST", body: {} });
check(paid.status === "CONFIRMED" && Number(paid.total) === 47970, "Checkout y pago emulado confirman el pedido con importes del servidor");
check((await publicClient.ok("/products/2")).stock === before.stock - 2, "Repetir confirmación no duplica el descuento de stock");
check((await customer.ok("/orders", auth)).some(item => item.id === order.id), "Compra recuperable desde el historial propio");
let routine = await customer.ok("/routines", { ...auth, method: "POST", body: { name: "Rutina Docker" } });
routine = await customer.ok(`/routines/${routine.id}/items/1`, { ...auth, method: "POST", body: { version: routine.version } });
check(routine.items.length === 1, "Rutina privada persistida con un ejercicio");
const receipt = await customer.ok("/my/contact", { ...auth, method: "POST", body: { submissionKey: randomUUID(), comment: "Consulta de prueba de Docker" } });
const own = (await customer.ok("/my/contact", auth)).items.find(item => item.reference === receipt.reference);
let message = await administration.ok(`/admin/contact/${own.id}`, { token: adminSession.accessToken });
message = await administration.ok(`/admin/contact/${own.id}/replies`, { token: adminSession.accessToken, method: "POST", body: { replyKey: randomUUID(), text: "Respuesta de prueba publicada", version: message.version } });
const internal = "Nota privada de prueba Docker";
await administration.ok(`/admin/contact/${own.id}`, { token: adminSession.accessToken, method: "PUT", body: { status: "EN_ATENCION", note: internal, version: message.version } });
const detail = await customer.ok(`/my/contact/${own.id}`, auth);
check(detail.replies.some(reply => reply.text === "Respuesta de prueba publicada") && !JSON.stringify(detail).includes(internal), "Consulta propia con respuesta publicada y nota interna protegida");
await customer.ok("/auth/logout", { method: "POST", cookieAuth: true });
check((await customer.send("/profile", auth)).status === 401, "Logout revoca también el JWT que estaba en memoria");

if (process.env.STACK_TEST_STATE_FILE) await writeFile(process.env.STACK_TEST_STATE_FILE, JSON.stringify({ client: clientCredentials, orderId: order.id, routineId: routine.id, contactId: own.id, checks }, null, 2));
console.log(JSON.stringify({ passed: checks.length, checks }, null, 2));
