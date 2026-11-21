package in.bits.campusops.catalog;

import in.bits.campusops.catalog.CatalogViews.AssetView;
import in.bits.campusops.catalog.CatalogViews.CategoryView;
import in.bits.campusops.catalog.CatalogViews.ServiceView;
import in.bits.campusops.catalog.CatalogViews.SlaPolicyView;
import in.bits.campusops.common.Exceptions.InvalidInputException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    public record CategoryRequest(@NotBlank @Pattern(regexp = "[A-Za-z_]{2,32}") String code,
                                  @NotBlank String name, String description, String keywords, String color,
                                  Boolean active) {
    }

    public record ServiceRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,40}") String code,
                                 @NotBlank String name, String description, @NotNull ServiceType type,
                                 @Min(1) @Max(5) int criticality, String location, @Min(0) int userBase,
                                 Long defaultCategoryId, Set<Long> dependsOn, Integer mapX, Integer mapY,
                                 Boolean active) {
    }

    public record AssetRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,40}") String tag,
                               @NotBlank String name, String location, @NotNull Long serviceId, Long categoryId) {
    }

    public record SlaRequest(Long serviceId, @NotNull Priority priority, @Min(1) int responseMinutes,
                             @Min(1) int resolutionMinutes) {
    }

    // ---- categories ----

    @GetMapping("/categories")
    public List<CategoryView> categories() {
        return catalog.allCategories().stream().map(CategoryView::of).toList();
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public CategoryView createCategory(@Valid @RequestBody CategoryRequest r) {
        return CategoryView.of(catalog.createCategory(r.code(), r.name(), r.description(), r.keywords(), r.color()));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public CategoryView updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest r) {
        return CategoryView.of(catalog.updateCategory(id, r.name(), r.description(), r.keywords(), r.color(),
                r.active() == null || r.active()));
    }

    // ---- services ----

    @GetMapping("/services")
    public List<ServiceView> services() {
        return catalog.allServices().stream().map(ServiceView::of).toList();
    }

    @GetMapping("/services/{id}")
    public ServiceView service(@PathVariable Long id) {
        return ServiceView.of(catalog.requireService(id));
    }

    @GetMapping("/services/{id}/blast-radius")
    public List<CatalogViews.ServiceRef> blastRadius(@PathVariable Long id) {
        return catalog.downstreamOf(catalog.requireService(id)).stream().map(CatalogViews.ServiceRef::of).toList();
    }

    @PostMapping("/services")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ServiceView createService(@Valid @RequestBody ServiceRequest r) {
        IncidentCategory cat = r.defaultCategoryId() == null ? null : catalog.requireCategory(r.defaultCategoryId());
        ItService saved;
        try {
            saved = catalog.saveService(new ItService(r.code(), r.name(), r.description(), r.type(), r.criticality(),
                    r.location(), r.userBase(), cat, r.mapX() == null ? 0 : r.mapX(), r.mapY() == null ? 0 : r.mapY()));
        } catch (IllegalArgumentException ex) {
            throw new InvalidInputException(ex.getMessage());
        }
        if (r.dependsOn() != null && !r.dependsOn().isEmpty()) {
            saved = catalog.setDependencies(saved.getId(), r.dependsOn());
        }
        return ServiceView.of(saved);
    }

    @PutMapping("/services/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ServiceView updateService(@PathVariable Long id, @Valid @RequestBody ServiceRequest r) {
        ItService s = catalog.requireService(id);
        IncidentCategory cat = r.defaultCategoryId() == null ? null : catalog.requireCategory(r.defaultCategoryId());
        try {
            s.update(r.name(), r.description(), r.type(), r.criticality(), r.location(), r.userBase(), cat,
                    r.active() == null || r.active());
            if (r.mapX() != null && r.mapY() != null) {
                s.moveOnMap(r.mapX(), r.mapY());
            }
        } catch (IllegalArgumentException ex) {
            throw new InvalidInputException(ex.getMessage());
        }
        if (r.dependsOn() != null) {
            s = catalog.setDependencies(id, r.dependsOn());
        }
        return ServiceView.of(catalog.saveService(s));
    }

    // ---- assets (QR stickers) ----

    @GetMapping("/assets")
    public List<AssetView> assets() {
        return catalog.allAssets().stream().map(AssetView::of).toList();
    }

    @GetMapping("/assets/{tag}")
    public AssetView asset(@PathVariable String tag) {
        return AssetView.of(catalog.requireAsset(tag));
    }

    @PostMapping("/assets")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public AssetView createAsset(@Valid @RequestBody AssetRequest r) {
        return AssetView.of(catalog.createAsset(r.tag(), r.name(), r.location(), r.serviceId(), r.categoryId()));
    }

    // ---- SLA policies ----

    @GetMapping("/sla-policies")
    public List<SlaPolicyView> policies() {
        return catalog.allPolicies().stream().map(SlaPolicyView::of).toList();
    }

    @PutMapping("/sla-policies")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public SlaPolicyView upsertPolicy(@Valid @RequestBody SlaRequest r) {
        return SlaPolicyView.of(catalog.upsertPolicy(r.serviceId(), r.priority(), r.responseMinutes(),
                r.resolutionMinutes()));
    }

    @DeleteMapping("/sla-policies/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public void deletePolicy(@PathVariable Long id) {
        catalog.deletePolicy(id);
    }
}
