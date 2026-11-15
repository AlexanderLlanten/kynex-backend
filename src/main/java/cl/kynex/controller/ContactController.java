package cl.kynex.controller;

import cl.kynex.dto.ContactDtos.*;
import cl.kynex.entity.ContactMessage.Status;
import cl.kynex.service.ContactService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class ContactController {
    private final ContactService contact;
    public ContactController(ContactService contact) { this.contact=contact; }
    @PostMapping("/contact") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Recibir una consulta pública",description="La referencia UUID permite reintentar el mismo contenido sin duplicarlo. Devuelve solo referencia y fecha; no envía correo.")
    @ApiResponses({@ApiResponse(responseCode="201",description="Recepción persistida o reintento del mismo contenido"),@ApiResponse(responseCode="400",description="Campos o referencia inválidos"),@ApiResponse(responseCode="409",description="Referencia utilizada para otro contenido")})
    public Receipt receive(@Valid @RequestBody Submission data) { return contact.receive(data); }
    @GetMapping("/admin/contact") @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary="Consultar la bandeja de contacto",description="Exclusivo de Administrador. Páginas de 20 solicitudes, de la más reciente a la más antigua. Filtro opcional por estado.")
    public Inbox inbox(Principal actor,@RequestParam(required=false) Status status,@RequestParam(defaultValue="0") int page) {
        return contact.inbox(Long.valueOf(actor.getName()),status,page);
    }
    @GetMapping("/admin/contact/{id}") @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary="Consultar una solicitud y su historial",description="Exclusivo de Administrador. El mensaje original no se modifica.")
    public Detail detail(Principal actor,@PathVariable Long id) { return contact.detail(Long.valueOf(actor.getName()),id); }
    @PutMapping("/admin/contact/{id}") @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary="Registrar atención interna",description="Requiere la versión vigente. Resolver exige una nota. Permite reabrir sin perder historial; no envía correos.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Atención guardada"),@ApiResponse(responseCode="400",description="Entrada inválida o resolución sin nota"),@ApiResponse(responseCode="401",description="Sesión no válida"),@ApiResponse(responseCode="403",description="Rol no autorizado"),@ApiResponse(responseCode="404",description="Solicitud inexistente"),@ApiResponse(responseCode="409",description="Versión desactualizada")})
    public Detail attend(Principal actor,@PathVariable Long id,@Valid @RequestBody Attention data) {
        return contact.attend(Long.valueOf(actor.getName()),id,data);
    }
}
