package cl.kynex.dto;

import cl.kynex.entity.ContactMessage.Status;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class ContactDtos {
    private ContactDtos() {}
    public record Submission(@NotNull UUID submissionKey,@NotBlank @Size(max=100) String name,
        @NotBlank @Size(max=100) String email,@NotBlank @Size(max=500) String comment) {}
    // La respuesta pública no expone identificadores internos, datos del remitente ni atención.
    public record Receipt(UUID reference,Instant receivedAt) {}
    public record AccountSubmission(@NotNull UUID submissionKey,@NotBlank @Size(max=500) String comment) {}
    public record Claim(@NotNull UUID reference) {}
    public record AccountLink(@NotNull @Min(0) Long version) {}
    public record ReplyInput(@NotNull UUID replyKey,@NotBlank @Size(max=2000) String text,@NotNull @Min(0) Long version) {}
    public record ReplyView(String text,Instant createdAt) {}
    public record OwnSummary(Long id,UUID reference,String preview,Status status,Instant createdAt) {}
    public record OwnInbox(List<OwnSummary> items,int page,int totalPages,long totalElements) {}
    // Esta vista nunca contiene la nota, el historial interno ni la identidad del administrador.
    public record OwnDetail(Long id,UUID reference,String name,String email,String comment,Status status,Instant createdAt,List<ReplyView> replies) {}
    public record Attention(@NotNull Status status,@NotNull @Size(max=1000) String note,@NotNull @Min(0) Long version) {}
    public record Summary(Long id,String name,String email,Status status,Instant createdAt,Instant updatedAt) {}
    public record Inbox(List<Summary> items,int page,int totalPages,long totalElements) {}
    public record EventView(String actorName,Status status,String note,Instant createdAt) {}
    public record Detail(Long id,String name,String email,String comment,Status status,String note,long version,
        Instant createdAt,Instant updatedAt,List<EventView> history,UUID reference,boolean linked,List<ReplyView> replies) {}
}
