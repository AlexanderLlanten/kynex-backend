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
    public record Attention(@NotNull Status status,@NotNull @Size(max=1000) String note,@NotNull @Min(0) Long version) {}
    public record Summary(Long id,String name,String email,Status status,Instant createdAt,Instant updatedAt) {}
    public record Inbox(List<Summary> items,int page,int totalPages,long totalElements) {}
    public record EventView(String actorName,Status status,String note,Instant createdAt) {}
    public record Detail(Long id,String name,String email,String comment,Status status,String note,long version,
        Instant createdAt,Instant updatedAt,List<EventView> history) {}
}
