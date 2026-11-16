package cl.kynex.contact.repository;

import cl.kynex.contact.model.ContactReply;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactReplyRepository extends JpaRepository<ContactReply,Long> {
    List<ContactReply> findByMessageIdOrderById(Long messageId);
    Optional<ContactReply> findByReplyKey(UUID key);
}
