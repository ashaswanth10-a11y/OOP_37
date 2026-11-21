package in.bits.campusops.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditRecord, Long> {

    List<AuditRecord> findByIncidentIdOrderByAtAscIdAsc(Long incidentId);

    Page<AuditRecord> findAllByOrderByAtDescIdDesc(Pageable pageable);

    Page<AuditRecord> findByAtAfterOrderByAtDescIdDesc(Instant after, Pageable pageable);
}
