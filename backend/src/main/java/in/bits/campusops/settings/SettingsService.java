package in.bits.campusops.settings;

import in.bits.campusops.common.Exceptions.InvalidInputException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SettingsService {

    public interface SettingsRepository extends JpaRepository<SystemSettings, Long> {
    }

    private final SettingsRepository repository;

    public SettingsService(SettingsRepository repository) {
        this.repository = repository;
    }

    public SystemSettings get() {
        return repository.findById(1L).orElseGet(() -> repository.save(new SystemSettings()));
    }

    public SystemSettings update(String assignmentStrategy, String priorityStrategy, int warnPercent,
                                 double duplicateThreshold, int windowMinutes, int minIncidents,
                                 boolean autoEscalation, boolean aiTriage) {
        SystemSettings s = get();
        try {
            s.update(assignmentStrategy, priorityStrategy, warnPercent, duplicateThreshold, windowMinutes,
                    minIncidents, autoEscalation, aiTriage);
        } catch (IllegalArgumentException ex) {
            throw new InvalidInputException(ex.getMessage());
        }
        return s;
    }
}
