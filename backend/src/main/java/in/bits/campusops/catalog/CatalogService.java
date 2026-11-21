package in.bits.campusops.catalog;

import in.bits.campusops.catalog.CatalogRepositories.AssetRepository;
import in.bits.campusops.catalog.CatalogRepositories.CategoryRepository;
import in.bits.campusops.catalog.CatalogRepositories.ServiceRepository;
import in.bits.campusops.catalog.CatalogRepositories.SlaPolicyRepository;
import in.bits.campusops.common.Exceptions.InvalidInputException;
import in.bits.campusops.common.Exceptions.MissingSlaConfigurationException;
import in.bits.campusops.common.Exceptions.ResourceNotFoundException;
import in.bits.campusops.common.Exceptions.ServiceNotFoundException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogService {

    private final CategoryRepository categories;
    private final ServiceRepository services;
    private final AssetRepository assets;
    private final SlaPolicyRepository slaPolicies;

    public CatalogService(CategoryRepository categories, ServiceRepository services, AssetRepository assets,
                          SlaPolicyRepository slaPolicies) {
        this.categories = categories;
        this.services = services;
        this.assets = assets;
        this.slaPolicies = slaPolicies;
    }

    // ---- categories -------------------------------------------------------

    @Transactional(readOnly = true)
    public List<IncidentCategory> activeCategories() {
        return categories.findByActiveTrueOrderByName();
    }

    @Transactional(readOnly = true)
    public List<IncidentCategory> allCategories() {
        return categories.findAll();
    }

    @Transactional(readOnly = true)
    public IncidentCategory requireCategory(Long id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    public IncidentCategory createCategory(String code, String name, String description, String keywords,
                                           String color) {
        if (categories.findByCode(code.toUpperCase()).isPresent()) {
            throw new InvalidInputException("Category code " + code + " already exists");
        }
        return categories.save(new IncidentCategory(code, name, description, keywords, color));
    }

    public IncidentCategory updateCategory(Long id, String name, String description, String keywords,
                                           String color, boolean active) {
        IncidentCategory c = requireCategory(id);
        c.update(name, description, keywords, color, active);
        return c;
    }

    // ---- services and the dependency graph --------------------------------

    @Transactional(readOnly = true)
    public List<ItService> allServices() {
        return services.findAllByOrderByName();
    }

    @Transactional(readOnly = true)
    public ItService requireService(Long id) {
        if (id == null) {
            throw new InvalidInputException("Choose the affected service");
        }
        return services.findById(id).orElseThrow(() -> new ServiceNotFoundException(id));
    }

    public ItService saveService(ItService service) {
        return services.save(service);
    }

    public ItService setDependencies(Long serviceId, Set<Long> upstreamIds) {
        ItService service = requireService(serviceId);
        Set<ItService> upstream = new HashSet<>();
        for (Long id : upstreamIds) {
            upstream.add(requireService(id));
        }
        for (ItService u : upstream) {
            if (u.equals(service) || upstreamOf(u).contains(service)) {
                throw new InvalidInputException(u.getName() + " already depends on " + service.getName()
                        + "; that would create a loop");
            }
        }
        service.replaceDependencies(upstream);
        return service;
    }

    /** Every service that directly or indirectly depends on {@code root}: its blast radius. */
    @Transactional(readOnly = true)
    public Set<ItService> downstreamOf(ItService root) {
        Map<Long, Set<ItService>> dependents = new HashMap<>();
        for (ItService s : services.findAll()) {
            for (ItService up : s.getDependsOn()) {
                dependents.computeIfAbsent(up.getId(), k -> new LinkedHashSet<>()).add(s);
            }
        }
        Set<ItService> seen = new LinkedHashSet<>();
        Deque<ItService> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            ItService current = queue.poll();
            for (ItService next : dependents.getOrDefault(current.getId(), Set.of())) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    /** Every service that {@code service} relies on, directly or indirectly. */
    @Transactional(readOnly = true)
    public Set<ItService> upstreamOf(ItService service) {
        Set<ItService> seen = new LinkedHashSet<>();
        Deque<ItService> queue = new ArrayDeque<>(service.getDependsOn());
        while (!queue.isEmpty()) {
            ItService current = queue.poll();
            if (seen.add(current)) {
                queue.addAll(current.getDependsOn());
            }
        }
        return seen;
    }

    // ---- assets -------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Asset> allAssets() {
        return assets.findAllByOrderByTag();
    }

    @Transactional(readOnly = true)
    public Asset requireAsset(String tag) {
        return assets.findByTagIgnoreCase(tag).orElseThrow(() -> new ResourceNotFoundException("Asset", tag));
    }

    public Asset createAsset(String tag, String name, String location, Long serviceId, Long categoryId) {
        if (assets.findByTagIgnoreCase(tag).isPresent()) {
            throw new InvalidInputException("Asset tag " + tag + " already exists");
        }
        IncidentCategory category = categoryId == null ? null : requireCategory(categoryId);
        return assets.save(new Asset(tag, name, location, requireService(serviceId), category));
    }

    // ---- SLA policies -----------------------------------------------------

    /** The service-specific policy if one exists, else the campus default for that priority. */
    @Transactional(readOnly = true)
    public SlaPolicy policyFor(ItService service, Priority priority) {
        return slaPolicies.findByServiceAndPriority(service, priority)
                .or(() -> slaPolicies.findByServiceIsNullAndPriority(priority))
                .orElseThrow(() -> new MissingSlaConfigurationException(
                        "No SLA policy is configured for " + priority + ". Ask an administrator to add one."));
    }

    @Transactional(readOnly = true)
    public List<SlaPolicy> allPolicies() {
        return slaPolicies.findAllByOrderByIdAsc();
    }

    public SlaPolicy upsertPolicy(Long serviceId, Priority priority, int responseMinutes, int resolutionMinutes) {
        ItService service = serviceId == null ? null : requireService(serviceId);
        SlaPolicy policy = (service == null
                ? slaPolicies.findByServiceIsNullAndPriority(priority)
                : slaPolicies.findByServiceAndPriority(service, priority)).orElse(null);
        try {
            if (policy == null) {
                return slaPolicies.save(new SlaPolicy(service, priority, responseMinutes, resolutionMinutes));
            }
            policy.update(responseMinutes, resolutionMinutes);
            return policy;
        } catch (IllegalArgumentException ex) {
            throw new InvalidInputException(ex.getMessage());
        }
    }

    public void deletePolicy(Long id) {
        SlaPolicy policy = slaPolicies.findById(id).orElseThrow(() -> new ResourceNotFoundException("SLA policy", id));
        if (policy.getService() == null) {
            throw new InvalidInputException("Campus default policies can be edited but not deleted");
        }
        slaPolicies.delete(policy);
    }
}
