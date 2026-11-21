package in.bits.campusops.incident;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    Optional<Incident> findByNumberIgnoreCase(String number);

    @Query("select coalesce(max(i.sequenceNo), 1000) from Incident i")
    int maxSequence();

    List<Incident> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    @Query("select i from Incident i where :userId member of i.supporterIds order by i.createdAt desc")
    List<Incident> findSupportedBy(@Param("userId") Long userId);

    List<Incident> findByAssigneeIdAndStatusInOrderByCreatedAtDesc(Long assigneeId, Collection<IncidentStatus> statuses);

    List<Incident> findByStatusIn(Collection<IncidentStatus> statuses);

    List<Incident> findByStatusInAndCreatedAtAfter(Collection<IncidentStatus> statuses, Instant after);

    List<Incident> findByCreatedAtAfter(Instant after);

    List<Incident> findByProblemIdOrderByCreatedAtAsc(Long problemId);

    @Query("select i from Incident i where i.status in :statuses and i.resolution.resolutionSummary is not null")
    List<Incident> findFixedWithResolution(@Param("statuses") Collection<IncidentStatus> statuses);

    @Query("select i.assignee.id, count(i) from Incident i where i.assignee is not null and i.status in :statuses "
            + "group by i.assignee.id")
    List<Object[]> countByAssignee(@Param("statuses") Collection<IncidentStatus> statuses);

    @Query("select i.assignee.id, count(i) from Incident i where i.assignee is not null and i.service.id = :serviceId "
            + "and i.status in :statuses group by i.assignee.id")
    List<Object[]> countFixedOnServiceByAssignee(@Param("serviceId") Long serviceId,
                                                 @Param("statuses") Collection<IncidentStatus> statuses);

    default List<Object[]> countActiveByAssignee() {
        return countByAssignee(IncidentStatus.ACTIVE_WORK);
    }

    default List<Incident> findOpen() {
        return findByStatusIn(IncidentStatus.OPEN);
    }
}
