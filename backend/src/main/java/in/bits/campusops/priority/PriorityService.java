package in.bits.campusops.priority;

import in.bits.campusops.common.Exceptions.InvalidInputException;
import in.bits.campusops.settings.SettingsService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Picks the strategy currently chosen by the administrator and delegates to it. */
@Service
public class PriorityService {

    private final Map<String, PriorityStrategy> strategies;
    private final SettingsService settings;

    public PriorityService(List<PriorityStrategy> strategies, SettingsService settings) {
        this.strategies = strategies.stream().collect(Collectors.toMap(PriorityStrategy::key, Function.identity()));
        this.settings = settings;
    }

    public PriorityStrategy.Assessment assess(PriorityStrategy.Input input) {
        return active().assess(input);
    }

    public PriorityStrategy active() {
        return require(settings.get().getPriorityStrategy());
    }

    public PriorityStrategy require(String key) {
        PriorityStrategy s = strategies.get(key);
        if (s == null) {
            throw new InvalidInputException("Unknown priority strategy " + key);
        }
        return s;
    }

    public List<PriorityStrategy> all() {
        return strategies.values().stream().sorted((a, b) -> a.key().compareTo(b.key())).toList();
    }
}
