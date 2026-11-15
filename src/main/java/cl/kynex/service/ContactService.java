package cl.kynex.service;

import cl.kynex.dto.ContactDtos.*;
import cl.kynex.entity.*;
import cl.kynex.entity.ContactMessage.Status;
import cl.kynex.exception.ApiException;
import cl.kynex.repository.*;
import java.time.Instant;
import java.util.Locale;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recepción pública y atención interna con autorización vigente e historial. */
@Service @Transactional
public class ContactService {
    private final ContactMessageRepository messages;
    private final ContactEventRepository events;
    private final UserRepository users;
    private final AccountPolicyRepository policy;
    public ContactService(ContactMessageRepository messages,ContactEventRepository events,UserRepository users,AccountPolicyRepository policy) {
        this.messages=messages;this.events=events;this.users=users;this.policy=policy;
    }
    public Receipt receive(Submission data) {
        var name=data.name().trim();var email=data.email().trim().toLowerCase(Locale.ROOT);var comment=data.comment().trim();
        if(name.isEmpty()||comment.isEmpty())throw ApiException.invalid("Completa nombre y comentario.");
        if(!email.matches("[^\\s@]+@(duoc\\.cl|profesor\\.duoc\\.cl|gmail\\.com)"))
            throw ApiException.invalid("Utiliza un correo @duoc.cl, @profesor.duoc.cl o @gmail.com.");
        messages.receive(data.submissionKey(),name,email,comment);
        var saved=messages.findBySubmissionKey(data.submissionKey()).orElseThrow(ApiException::missing);
        if(!saved.name.equals(name)||!saved.email.equals(email)||!saved.comment.equals(comment))
            throw ApiException.conflict("La referencia ya corresponde a otro mensaje. Inicia un nuevo envío.");
        return new Receipt(saved.submissionKey,saved.createdAt);
    }
    private AppUser admin(Long actor) {
        policy.lockPolicy();var account=users.findById(actor).orElseThrow(ApiException::missing);
        if(!account.active||account.role!=Role.ADMINISTRADOR)throw new ApiException(HttpStatus.FORBIDDEN,"Se requiere una cuenta de Administrador activa.");
        return account;
    }
    public Inbox inbox(Long actor,Status status,int page) {
        admin(actor);if(page<0||page>100000)throw ApiException.invalid("La página debe estar entre 0 y 100000.");
        var paging=PageRequest.of(page,20,Sort.by(Sort.Direction.DESC,"createdAt","id"));
        var result=status==null?messages.findAll(paging):messages.findByStatus(status,paging);
        return new Inbox(result.map(m->new Summary(m.id,m.name,m.email,m.status,m.createdAt,m.updatedAt)).getContent(),page,result.getTotalPages(),result.getTotalElements());
    }
    public Detail detail(Long actor,Long id) { admin(actor);return view(messages.findById(id).orElseThrow(ApiException::missing)); }
    public Detail attend(Long actor,Long id,Attention data) {
        var account=admin(actor);var m=messages.lockById(id).orElseThrow(ApiException::missing);
        if(!m.version.equals(data.version()))throw ApiException.conflict("La atención cambió. Recarga antes de guardar.");
        var note=data.note().trim();
        if(data.status()==Status.RESUELTA&&note.isEmpty())throw ApiException.invalid("Describe la atención antes de resolver la consulta.");
        if(m.status==data.status()&&m.note.equals(note))return view(m);
        m.status=data.status();m.note=note;m.updatedAt=Instant.now();messages.saveAndFlush(m);
        var event=new ContactEvent();event.messageId=m.id;event.actorId=actor;event.actorName=account.firstName+" "+account.lastName;
        event.status=m.status;event.note=m.note;event.createdAt=m.updatedAt;events.saveAndFlush(event);
        return view(m);
    }
    private Detail view(ContactMessage m) {
        var history=events.findByMessageIdOrderById(m.id).stream().map(e->new EventView(e.actorName,e.status,e.note,e.createdAt)).toList();
        return new Detail(m.id,m.name,m.email,m.comment,m.status,m.note,m.version,m.createdAt,m.updatedAt,history);
    }
}
