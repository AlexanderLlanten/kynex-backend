package cl.kynex.contact.service;

import cl.kynex.identity.api.AccountAccess;
import cl.kynex.contact.model.ContactEvent;
import cl.kynex.contact.model.ContactMessage;
import cl.kynex.contact.model.ContactReply;
import cl.kynex.contact.repository.ContactEventRepository;
import cl.kynex.contact.repository.ContactMessageRepository;
import cl.kynex.contact.repository.ContactReplyRepository;

import cl.kynex.contact.dto.ContactDtos.*;
import cl.kynex.contact.model.ContactMessage.Status;
import cl.kynex.shared.exception.ApiException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recepción pública y atención interna con autorización vigente e historial. */
@Service @Transactional
public class ContactService {
    private final ContactMessageRepository messages;
    private final ContactEventRepository events;
    private final ContactReplyRepository replies;
    private final AccountAccess accounts;
    public ContactService(ContactMessageRepository messages,ContactEventRepository events,ContactReplyRepository replies,AccountAccess accounts) {
        this.messages=messages;this.events=events;this.replies=replies;this.accounts=accounts;
    }
    public Receipt receive(Submission data) {
        return receive(data,null);
    }
    private Receipt receive(Submission data,Long owner) {
        var name=data.name().trim();var email=data.email().trim().toLowerCase(Locale.ROOT);var comment=data.comment().trim();
        if(name.isEmpty()||comment.isEmpty())throw ApiException.invalid("Completa nombre y comentario.");
        if(owner==null&&!email.matches("[^\\s@]+@(duoc\\.cl|profesor\\.duoc\\.cl|gmail\\.com)"))
            throw ApiException.invalid("Utiliza un correo @duoc.cl, @profesor.duoc.cl o @gmail.com.");
        messages.receive(data.submissionKey(),name,email,comment,owner);
        var saved=messages.findBySubmissionKey(data.submissionKey()).orElseThrow(ApiException::missing);
        if(!saved.name.equals(name)||!saved.email.equals(email)||!saved.comment.equals(comment)||!Objects.equals(saved.ownerId,owner))
            throw ApiException.conflict("La referencia ya corresponde a otro mensaje. Inicia un nuevo envío.");
        return new Receipt(saved.submissionKey,saved.createdAt);
    }
    public Receipt receiveOwned(Long owner,AccountSubmission data) {
        var user=accounts.lockActive(owner);var full=(user.firstName()+" "+user.lastName()).trim();
        var name=full.substring(0,full.offsetByCodePoints(0,Math.min(100,full.codePointCount(0,full.length()))));
        return receive(new Submission(data.submissionKey(),name,user.email(),data.comment()),owner);
    }
    private Pageable page(int number) {
        if(number<0||number>100000)throw ApiException.invalid("La página debe estar entre 0 y 100000.");
        return PageRequest.of(number,20,Sort.by(Sort.Direction.DESC,"createdAt","id"));
    }
    public OwnInbox mine(Long owner,int number) {
        accounts.lockActive(owner);var result=messages.findByOwnerId(owner,page(number));
        return new OwnInbox(result.map(m->new OwnSummary(m.id,m.submissionKey,m.comment.length()>140?m.comment.substring(0,140)+"…":m.comment,m.status,m.createdAt)).getContent(),number,result.getTotalPages(),result.getTotalElements());
    }
    public OwnDetail mine(Long owner,Long id) {
        accounts.lockActive(owner);return ownView(messages.findByIdAndOwnerId(id,owner).orElseThrow(ApiException::missing));
    }
    public OwnDetail claim(Long owner,Claim data) {
        var user=accounts.lockActive(owner);var m=messages.lockByReference(data.reference()).orElseThrow(ApiException::missing);
        if(m.ownerId!=null&&!m.ownerId.equals(owner))throw ApiException.missing();
        if(m.ownerId==null) {
            if(!m.email.equalsIgnoreCase(user.email().trim()))throw ApiException.missing();
            m.ownerId=owner;m.updatedAt=Instant.now();messages.saveAndFlush(m);
        }
        return ownView(m);
    }
    private java.util.List<ReplyView> published(Long id) { return replies.findByMessageIdOrderById(id).stream().map(r->new ReplyView(r.text,r.createdAt)).toList(); }
    private OwnDetail ownView(ContactMessage m) { return new OwnDetail(m.id,m.submissionKey,m.name,m.email,m.comment,m.status,m.createdAt,published(m.id)); }
    private AccountAccess.Account admin(Long actor) {
        return accounts.requireAdministrator(actor);
    }
    public Inbox inbox(Long actor,Status status,int page) {
        admin(actor);var paging=page(page);
        var result=status==null?messages.findAll(paging):messages.findByStatus(status,paging);
        return new Inbox(result.map(m->new Summary(m.id,m.name,m.email,m.status,m.createdAt,m.updatedAt)).getContent(),page,result.getTotalPages(),result.getTotalElements());
    }
    public Detail detail(Long actor,Long id) { admin(actor);return view(messages.findById(id).orElseThrow(ApiException::missing)); }
    /** Recuperación del histórico confirmada por Administración; no concede acceso por correo al iniciar sesión. */
    public Detail linkAccount(Long actor,Long id,AccountLink data) {
        var administrator=admin(actor);var initial=messages.findById(id).orElseThrow(ApiException::missing);
        if(initial.ownerId!=null)return view(initial);
        var candidate=accounts.findActiveByEmail(initial.email)
            .orElseThrow(()->ApiException.invalid("No existe una cuenta activa con el correo de esta consulta."));
        var account=accounts.lockActive(candidate.id());var message=messages.lockById(id).orElseThrow(ApiException::missing);
        if(message.ownerId!=null)return view(message);
        if(!message.version.equals(data.version()))throw ApiException.conflict("La consulta cambió. Recarga antes de vincularla.");
        if(!message.email.equalsIgnoreCase(account.email()))throw ApiException.conflict("El correo de la cuenta cambió. Recarga antes de vincularla.");
        message.ownerId=account.id();message.updatedAt=Instant.now();messages.saveAndFlush(message);
        var event=new ContactEvent();event.messageId=id;event.actorId=actor;event.actorName=administrator.firstName()+" "+administrator.lastName();
        event.status=message.status;event.note="Vinculación a la cuenta coincidente confirmada por Administración.";event.createdAt=message.updatedAt;events.saveAndFlush(event);
        return view(message);
    }
    public Detail reply(Long actor,Long id,ReplyInput data) {
        var account=admin(actor);var m=messages.lockById(id).orElseThrow(ApiException::missing);var text=data.text().trim();
        if(text.isEmpty())throw ApiException.invalid("Escribe la respuesta para el usuario.");
        var previous=replies.findByReplyKey(data.replyKey());
        if(previous.isPresent()) {
            var reply=previous.get();
            if(!reply.messageId.equals(id)||!Objects.equals(reply.actorId,actor)||!reply.text.equals(text))throw ApiException.conflict("La referencia ya corresponde a otra respuesta.");
            return view(m);
        }
        if(!m.version.equals(data.version()))throw ApiException.conflict("La atención cambió. Recarga antes de enviar la respuesta.");
        m.updatedAt=Instant.now();
        if(m.status==Status.PENDIENTE) {
            m.status=Status.EN_ATENCION;
            var event=new ContactEvent();event.messageId=id;event.actorId=actor;event.actorName=account.firstName()+" "+account.lastName();
            event.status=m.status;event.note=m.note;event.createdAt=m.updatedAt;events.saveAndFlush(event);
        }
        messages.saveAndFlush(m);
        var reply=new ContactReply();reply.messageId=id;reply.replyKey=data.replyKey();reply.actorId=actor;
        reply.actorName=account.firstName()+" "+account.lastName();reply.text=text;reply.createdAt=m.updatedAt;replies.saveAndFlush(reply);
        return view(m);
    }
    public Detail attend(Long actor,Long id,Attention data) {
        var account=admin(actor);var m=messages.lockById(id).orElseThrow(ApiException::missing);
        if(!m.version.equals(data.version()))throw ApiException.conflict("La atención cambió. Recarga antes de guardar.");
        var note=data.note().trim();
        if(data.status()==Status.RESUELTA&&note.isEmpty())throw ApiException.invalid("Describe la atención antes de resolver la consulta.");
        if(m.status==data.status()&&m.note.equals(note))return view(m);
        m.status=data.status();m.note=note;m.updatedAt=Instant.now();messages.saveAndFlush(m);
        var event=new ContactEvent();event.messageId=m.id;event.actorId=actor;event.actorName=account.firstName()+" "+account.lastName();
        event.status=m.status;event.note=m.note;event.createdAt=m.updatedAt;events.saveAndFlush(event);
        return view(m);
    }
    private Detail view(ContactMessage m) {
        var history=events.findByMessageIdOrderById(m.id).stream().map(e->new EventView(e.actorName,e.status,e.note,e.createdAt)).toList();
        return new Detail(m.id,m.name,m.email,m.comment,m.status,m.note,m.version,m.createdAt,m.updatedAt,history,m.submissionKey,m.ownerId!=null,published(m.id));
    }
}
