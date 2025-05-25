import { useCallback } from 'react';
import ReactFlow, {
  Background,
  Controls,
  MiniMap,
  addEdge,
} from 'reactflow';
import type { Connection, Edge, Node } from 'reactflow';
import 'reactflow/dist/style.css';

import { BookmarkNode } from './BookmarkNode';
import { useCanvasStore } from '../store/canvasStore';

const nodeTypes = {
  bookmark: BookmarkNode,
};

export const Canvas = () => {
  const {
    nodes,
    edges,
    isGridVisible,
    addConnection,
    updateBookmark,
  } = useCanvasStore();

  const onConnect = useCallback(
    (params: Connection) => {
      if (params.source && params.target) {
        addConnection({
          source: params.source,
          target: params.target,
          type: 'default',
        });
      }
    },
    [addConnection]
  );

  const onNodeDragStop = useCallback(
    (_event: any, node: Node) => {
      updateBookmark(node.id, { position: node.position });
    },
    [updateBookmark]
  );

  const onNodesChange = useCallback((changes: any) => {
    // Handle position changes during drag for visual feedback
    changes.forEach((change: any) => {
      if (change.type === 'position' && change.dragging) {
        updateBookmark(change.id, { position: change.position });
      }
    });
  }, [updateBookmark]);

  const onEdgesChange = useCallback(() => {
    // Handle edge changes if needed  
  }, []);

  return (
    <div className="canvas-container">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onNodeDragStop={onNodeDragStop}
        nodeTypes={nodeTypes}
        fitView
        attributionPosition="top-right"
      >
        <Background
          variant="dots"
          gap={20}
          size={1}
          style={{ opacity: isGridVisible ? 0.5 : 0 }}
        />
        <Controls />
        <MiniMap />
      </ReactFlow>
    </div>
  );
};