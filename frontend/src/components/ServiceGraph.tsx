import { useEffect, useMemo } from "react"
import { Background, Controls, Handle, MarkerType, Position, ReactFlow, useNodesInitialized, useReactFlow, type Edge, type Node, type NodeProps } from "@xyflow/react"
import { HEALTH_LABEL, HEALTH_VAR } from "../lib/format"
import type { MapNode } from "../lib/types"

type Data = { [K in keyof MapNode]: MapNode[K] } & { selected: boolean; dimmed: boolean; inBlast: boolean; publicView: boolean }
type ServiceNode = Node<Data, "service">

function ServiceCard({ data }: NodeProps<ServiceNode>) {
  const c = HEALTH_VAR[data.health]
  const hot = data.health === "OUTAGE"
  return (
    <div
      className="w-[220px] rounded-md border bg-card text-left transition-opacity"
      style={{
        borderColor: data.selected ? "var(--beacon)" : data.inBlast ? "var(--beacon)" : "var(--line-strong)",
        boxShadow: data.selected
          ? "0 0 0 3px color-mix(in srgb, var(--beacon) 40%, transparent)"
          : data.rootCause
            ? "0 0 0 3px color-mix(in srgb, var(--outage) 35%, transparent)"
            : "none",
        opacity: data.dimmed ? 0.35 : 1,
      }}
    >
      <Handle type="target" position={Position.Left} className="!h-1.5 !w-1.5 !border-0 !bg-line-strong" />
      <div className="flex">
        <div className={"w-1.5 shrink-0 rounded-l-md " + (data.health === "IMPACTED" ? "hatch" : "")} style={{ background: c }} />
        <div className="min-w-0 flex-1 px-2.5 py-1.5">
          <div className="flex items-center gap-2">
            <span className="font-mono text-[10.5px] text-mute">{data.code}</span>
            {data.rootCause && (
              <span className="ml-auto rounded-[3px] bg-outage px-1 font-mono text-[10px] font-semibold text-white">
                ROOT {data.problemNumber ?? ""}
              </span>
            )}
          </div>
          <div className="truncate text-[12.5px] font-medium leading-tight text-ink">{data.name}</div>
          <div className="mt-0.5 flex items-center gap-2 text-[11px]">
            <span className={"font-semibold " + (hot ? "lamp-blink" : "")} style={{ color: c }}>
              {HEALTH_LABEL[data.health]}
            </span>
            {!data.publicView && data.openIncidents > 0 && (
              <span className="ml-auto font-mono text-ink-2">
                {data.openIncidents} open{data.worstPriority ? " · " + data.worstPriority : ""}
              </span>
            )}
          </div>
        </div>
      </div>
      <Handle type="source" position={Position.Right} className="!h-1.5 !w-1.5 !border-0 !bg-line-strong" />
    </div>
  )
}

const nodeTypes = { service: ServiceCard }

function AutoFit({ count }: { count: number }) {
  const { fitView } = useReactFlow()
  const ready = useNodesInitialized()
  useEffect(() => {
    if (!ready) return
    const t = setTimeout(() => fitView({ padding: 0.06, duration: 0 }), 60)
    return () => clearTimeout(t)
  }, [ready, count, fitView])
  return null
}

export function ServiceGraph({
  nodes,
  edges,
  selectedId,
  blast,
  onSelect,
  publicView,
}: {
  nodes: MapNode[]
  edges: { from: number; to: number }[]
  selectedId?: number | null
  blast?: Set<number>
  onSelect?: (id: number | null) => void
  publicView?: boolean
}) {
  const healthOf = useMemo(() => new Map(nodes.map((n) => [n.id, n.health])), [nodes])

  const flowNodes: ServiceNode[] = useMemo(
    () =>
      nodes.map((n) => ({
        id: String(n.id),
        type: "service",
        position: { x: n.x * 300, y: n.y * 95 },
        data: {
          ...n,
          selected: n.id === selectedId,
          inBlast: !!blast?.has(n.id),
          dimmed: !!selectedId && !!blast && n.id !== selectedId && !blast.has(n.id),
          publicView: !!publicView,
        },
        draggable: false,
        connectable: false,
      })),
    [nodes, selectedId, blast, publicView],
  )

  const flowEdges: Edge[] = useMemo(
    () =>
      edges.map((e) => {
        const fromBad = healthOf.get(e.from) === "OUTAGE"
        const toBad = healthOf.get(e.to) !== "OPERATIONAL"
        const lit = !!selectedId && !!blast && (e.from === selectedId || blast.has(e.from)) && blast.has(e.to)
        const color = lit ? "var(--beacon)" : fromBad && toBad ? "var(--outage)" : "var(--line-strong)"
        return {
          id: e.from + "-" + e.to,
          source: String(e.from),
          target: String(e.to),
          animated: lit || (fromBad && toBad),
          style: { stroke: color, strokeWidth: lit ? 2.2 : fromBad && toBad ? 1.8 : 1.1, opacity: selectedId && !lit ? 0.35 : 1 },
          markerEnd: { type: MarkerType.ArrowClosed, color, width: 14, height: 14 },
        }
      }),
    [edges, healthOf, selectedId, blast],
  )

  return (
    <ReactFlow
      nodes={flowNodes}
      edges={flowEdges}
      nodeTypes={nodeTypes}
      fitView
      fitViewOptions={{ padding: 0.06, duration: 0 }}
      minZoom={0.15}
      maxZoom={1.6}
      nodesDraggable={false}
      nodesConnectable={false}
      elementsSelectable={!!onSelect}
      onNodeClick={(_, n) => onSelect?.(Number(n.id))}
      onPaneClick={() => onSelect?.(null)}
      proOptions={{ hideAttribution: true }}
      style={{ background: "var(--paper)" }}
    >
      <Background gap={24} size={1.2} color="var(--line-strong)" bgColor="var(--paper)" />
      <Controls showInteractive={false} position="bottom-left" />
      <AutoFit count={nodes.length} />
    </ReactFlow>
  )
}
