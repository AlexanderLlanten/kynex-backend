package cl.kynex.contact.repository;

import cl.kynex.contact.model.ContactEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactEventRepository extends JpaRepository<ContactEvent,Long> {
    List<ContactEvent> findByMessageIdOrderById(Long messageId);
}
