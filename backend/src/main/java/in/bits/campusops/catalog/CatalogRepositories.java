package in.bits.campusops.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository pattern: persistence contracts for the service catalogue. */
public final class CatalogRepositories {

    private CatalogRepositories() {
    }

    public interface CategoryRepository extends JpaRepository<IncidentCategory, Long> {
        Optional<IncidentCategory> findByCode(String code);

        List<IncidentCategory> findByActiveTrueOrderByName();
    }

    public interface ServiceRepository extends JpaRepository<ItService, Long> {
        Optional<ItService> findByCode(String code);

        List<ItService> findAllByOrderByName();
    }

    public interface AssetRepository extends JpaRepository<Asset, Long> {
        Optional<Asset> findByTagIgnoreCase(String tag);

        List<Asset> findAllByOrderByTag();
    }

    public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {
        Optional<SlaPolicy> findByServiceAndPriority(ItService service, Priority priority);

        Optional<SlaPolicy> findByServiceIsNullAndPriority(Priority priority);

        List<SlaPolicy> findAllByOrderByIdAsc();
    }
}
