import ReactFlow, {
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  MarkerType,
} from 'reactflow';
import 'reactflow/dist/style.css';
import { useMemo, useEffect } from 'react';

import DbNode    from './nodes/DbNode.jsx';
import RouteNode from './nodes/RouteNode.jsx';
import QueueNode from './nodes/QueueNode.jsx';

const nodeTypes = { dbNode: DbNode, routeNode: RouteNode, queueNode: QueueNode };

// Static layout: Oracle → R1 → Q1 → R2 → Q2 → R3 → Q3 → R4 → Kinetica
// Positions are in pixels, React Flow uses a canvas coordinate system
const INITIAL_NODES = [
  { id: 'oracle',           type: 'dbNode',    position: { x: 0,    y: 110 }, data: { label: 'Oracle XE',    isSource: true,  recordCount: 0, sublabel: 'source' } },
  { id: 'fetchFromOracle',  type: 'routeNode', position: { x: 195,  y: 95  }, data: { routeMetric: {} } },
  { id: 'batchQueue',       type: 'queueNode', position: { x: 375,  y: 103 }, data: { queueMetric: {} } },
  { id: 'processBatch',     type: 'routeNode', position: { x: 545,  y: 95  }, data: { routeMetric: {} } },
  { id: 'transformQueue',   type: 'queueNode', position: { x: 725,  y: 103 }, data: { queueMetric: {} } },
  { id: 'transformRecord',  type: 'routeNode', position: { x: 895,  y: 95  }, data: { routeMetric: {} } },
  { id: 'insertQueue',      type: 'queueNode', position: { x: 1075, y: 103 }, data: { queueMetric: {} } },
  { id: 'insertToKinetica', type: 'routeNode', position: { x: 1245, y: 95  }, data: { routeMetric: {} } },
  { id: 'kinetica',         type: 'dbNode',    position: { x: 1435, y: 110 }, data: { label: 'Kinetica',     isSource: false, recordCount: 0, sublabel: 'target'  } },
];

const EDGES = [
  ['oracle', 'fetchFromOracle'],
  ['fetchFromOracle', 'batchQueue'],
  ['batchQueue', 'processBatch'],
  ['processBatch', 'transformQueue'],
  ['transformQueue', 'transformRecord'],
  ['transformRecord', 'insertQueue'],
  ['insertQueue', 'insertToKinetica'],
  ['insertToKinetica', 'kinetica'],
].map(([source, target], i) => ({
  id: `e${i}`,
  source,
  target,
  animated: false,
  style: { stroke: '#475569', strokeWidth: 2 },
  markerEnd: { type: MarkerType.ArrowClosed, color: '#475569' },
}));

function edgeColor(status) {
  if (status === 'RUNNING')   return '#3b82f6';
  if (status === 'COMPLETED') return '#10b981';
  return '#475569';
}

export default function PipelineFlow({ metrics }) {
  const [nodes, setNodes, onNodesChange] = useNodesState(INITIAL_NODES);
  const [edges, setEdges, onEdgesChange] = useEdgesState(EDGES);

  useEffect(() => {
    if (!metrics) return;
    const { routes, queues, recordsTransformed, recordsInsertedToKinetica } = metrics;

    setNodes(nds => nds.map(node => {
      if (node.type === 'dbNode') {
        if (node.id === 'oracle') {
          return { ...node, data: { ...node.data, recordCount: recordsTransformed } };
        }
        if (node.id === 'kinetica') {
          return { ...node, data: { ...node.data, recordCount: recordsInsertedToKinetica } };
        }
      }
      if (node.type === 'routeNode' && routes[node.id]) {
        return { ...node, data: { routeMetric: routes[node.id] } };
      }
      if (node.type === 'queueNode' && queues[node.id]) {
        return { ...node, data: { queueMetric: queues[node.id] } };
      }
      return node;
    }));

    // Animate edges where the downstream route is RUNNING
    setEdges(eds => eds.map(edge => {
      const targetRoute = routes[edge.target];
      const sourceRoute = routes[edge.source];
      const isActive =
        (targetRoute?.status === 'RUNNING') ||
        (sourceRoute?.status === 'RUNNING' && edge.target.endsWith('Queue'));
      const color = edgeColor(targetRoute?.status ?? sourceRoute?.status ?? 'IDLE');
      return {
        ...edge,
        animated: isActive,
        style: { stroke: color, strokeWidth: isActive ? 3 : 2 },
        markerEnd: { type: MarkerType.ArrowClosed, color },
      };
    }));
  }, [metrics, setNodes, setEdges]);

  return (
    <div className="w-full h-[320px] rounded-xl overflow-hidden border border-slate-700">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        nodeTypes={nodeTypes}
        fitView
        fitViewOptions={{ padding: 0.25 }}
        minZoom={0.4}
        maxZoom={1.5}
        attributionPosition="bottom-right"
      >
        <Background color="#334155" gap={20} />
        <Controls className="!bg-slate-800 !border-slate-600 !shadow-lg" />
        <MiniMap
          nodeColor={(n) => {
            if (n.type === 'dbNode')    return '#1d4ed8';
            if (n.type === 'routeNode') return '#0f172a';
            return '#334155';
          }}
          maskColor="#0f172a99"
          className="!bg-slate-900 !border-slate-700"
        />
      </ReactFlow>
    </div>
  );
}
