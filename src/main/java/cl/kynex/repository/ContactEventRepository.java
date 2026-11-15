package cl.kynex.repository;

import cl.kynex.entity.ContactEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactEventRepository extends JpaRepository<ContactEvent,Long> {
    List<ContactEvent> findByMessageIdOrderById(Long messageId);
}
