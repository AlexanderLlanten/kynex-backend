package cl.kynex.repository;

import cl.kynex.entity.ContactMessage;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ContactMessageRepository extends JpaRepository<ContactMessage,Long> {
    Optional<ContactMessage> findBySubmissionKey(UUID key);
    // ON CONFLICT espera una recepción concurrente y evita crear dos consultas al reintentar.
    @Modifying
    @Query(value="insert into contact_message(submission_key,name,email,comment) values(:key,:name,:email,:comment) on conflict (submission_key) do nothing",nativeQuery=true)
    int receive(UUID key,String name,String email,String comment);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ContactMessage m where m.id=:id") Optional<ContactMessage> lockById(Long id);
    Page<ContactMessage> findByStatus(ContactMessage.Status status,Pageable page);
}
