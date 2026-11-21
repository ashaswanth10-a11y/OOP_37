package in.bits.campusops.catalog;

import java.util.List;

public final class CatalogViews {

    private CatalogViews() {
    }

    public record CategoryView(Long id, String code, String name, String description, String keywords,
                               String color, boolean active) {
        public static CategoryView of(IncidentCategory c) {
            return c == null ? null : new CategoryView(c.getId(), c.getCode(), c.getName(), c.getDescription(),
                    c.getKeywords(), c.getColor(), c.isActive());
        }
    }

    public record ServiceRef(Long id, String code, String name) {
        public static ServiceRef of(ItService s) {
            return s == null ? null : new ServiceRef(s.getId(), s.getCode(), s.getName());
        }
    }

    public record ServiceView(Long id, String code, String name, String description, ServiceType type,
                              int criticality, String location, int userBase, CategoryView defaultCategory,
                              List<ServiceRef> dependsOn, int mapX, int mapY, boolean active) {
        public static ServiceView of(ItService s) {
            return new ServiceView(s.getId(), s.getCode(), s.getName(), s.getDescription(), s.getType(),
                    s.getCriticality(), s.getLocation(), s.getUserBase(), CategoryView.of(s.getDefaultCategory()),
                    s.getDependsOn().stream().map(ServiceRef::of)
                            .sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList(),
                    s.getMapX(), s.getMapY(), s.isActive());
        }
    }

    public record AssetView(Long id, String tag, String name, String location, ServiceRef service,
                            CategoryView category) {
        public static AssetView of(Asset a) {
            return new AssetView(a.getId(), a.getTag(), a.getName(), a.getLocation(), ServiceRef.of(a.getService()),
                    CategoryView.of(a.getCategory()));
        }
    }

    public record SlaPolicyView(Long id, ServiceRef service, Priority priority, int responseMinutes,
                                int resolutionMinutes) {
        public static SlaPolicyView of(SlaPolicy p) {
            return new SlaPolicyView(p.getId(), ServiceRef.of(p.getService()), p.getPriority(),
                    p.getResponseMinutes(), p.getResolutionMinutes());
        }
    }
}
