package in.bits.campusops.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** A physical thing with a QR sticker: a lab PC, an access point, a printer. */
@Entity
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Printed on the QR sticker, e.g. "AP-GAUTAM-2F". */
    @Column(nullable = false, unique = true, length = 40)
    private String tag;

    @Column(nullable = false)
    private String name;

    private String location;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private ItService service;

    @ManyToOne(fetch = FetchType.EAGER)
    private IncidentCategory category;

    protected Asset() {
    }

    public Asset(String tag, String name, String location, ItService service, IncidentCategory category) {
        this.tag = tag.toUpperCase();
        this.name = name;
        this.location = location;
        this.service = service;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public ItService getService() {
        return service;
    }

    public IncidentCategory getCategory() {
        return category;
    }
}
