package in.bits.campusops.problem;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<ProblemRecord, Long> {

    Optional<ProblemRecord> findFirstByRootServiceIdAndStatus(Long serviceId, ProblemRecord.Status status);

    List<ProblemRecord> findByStatusOrderByDetectedAtDesc(ProblemRecord.Status status);

    List<ProblemRecord> findAllByOrderByDetectedAtDesc();
}
