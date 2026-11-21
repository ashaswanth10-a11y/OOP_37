import { HEALTH_LABEL, HEALTH_VAR, TYPE_LABEL } from "../lib/format"
import type { Health, ServiceType } from "../lib/types"

type Lamp = { id: number; name: string; type: ServiceType; health: Health; y?: number; openIncidents?: number }

const ORDER: ServiceType[] = ["NETWORK", "INFRASTRUCTURE", "PLATFORM", "APPLICATION", "END_USER"]

export function LampBoard({
  lamps,
  size = "sm",
  showLabels,
}: {
  lamps: Lamp[]
  size?: "sm" | "lg"
  showLabels?: boolean
}) {
  const groups = ORDER.map((t) => ({
    type: t,
    items: lamps.filter((l) => l.type === t).sort((a, b) => (a.y ?? 0) - (b.y ?? 0)),
  })).filter((g) => g.items.length > 0)

  const dim = size === "sm" ? "h-3 w-2.5 shrink-0 rounded-[2px]" : "h-7 w-7 rounded-[4px]"

  return (
    <div className={"flex " + (size === "sm" ? "flex-nowrap gap-x-2" : "flex-wrap gap-x-6 gap-y-4")}>
      {groups.map((g) => (
        <div key={g.type}>
          {showLabels && <div className="eyebrow mb-1.5">{TYPE_LABEL[g.type]}</div>}
          <div className={"flex " + (size === "sm" ? "flex-nowrap gap-[3px]" : "flex-wrap gap-1.5")}>
            {g.items.map((l) => (
              <span
                key={l.id}
                role="img"
                title={l.name + ": " + HEALTH_LABEL[l.health] + (l.openIncidents ? " (" + l.openIncidents + " open)" : "")}
                aria-label={l.name + ": " + HEALTH_LABEL[l.health]}
                className={
                  "inline-block " +
                  dim +
                  " " +
                  (l.health === "IMPACTED" ? "hatch " : "") +
                  (l.health === "OUTAGE" ? "lamp-blink" : "")
                }
                style={{
                  background: HEALTH_VAR[l.health],
                  opacity: l.health === "OPERATIONAL" ? 0.85 : 1,
                }}
              />
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}

export function LampLegend() {
  return (
    <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-mute">
      {(["OPERATIONAL", "DEGRADED", "IMPACTED", "OUTAGE"] as Health[]).map((h) => (
        <span key={h} className="inline-flex items-center gap-1.5">
          <span className={"inline-block h-2.5 w-2.5 rounded-[2px] " + (h === "IMPACTED" ? "hatch" : "")} style={{ background: HEALTH_VAR[h] }} />
          {HEALTH_LABEL[h]}
        </span>
      ))}
    </div>
  )
}
