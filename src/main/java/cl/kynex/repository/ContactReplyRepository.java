package cl.kynex.repository;

import cl.kynex.entity.ContactReply;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactReplyRepository extends JpaRepository<ContactReply,Long> {
    List<ContactReply> findByMessageIdOrderById(Long messageId);
    Optional<ContactReply> findByReplyKey(UUID key);
}
