import { useCallback, useState, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import ReactFlow, {
  Background,
  Controls,
  MiniMap,
  addEdge,
  getConnectedEdges,
  getIncomers,
  getOutgoers,
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
    removeConnectionBetween,
    updateBookmark,
  } = useCanvasStore();

  const [selectedNodes, setSelectedNodes] = useState<string[]>([]);
  const [connectingFrom, setConnectingFrom] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  
  // Use refs for connection line to avoid re-renders
  const connectionLineRef = useRef<SVGLineElement>(null);
  const connectionStartRef = useRef<{ x: number; y: number } | null>(null);




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
      if (change.type === 'select') {
        if (change.selected) {
          setSelectedNodes(prev => [...prev.filter(id => id !== change.id), change.id]);
        } else {
          setSelectedNodes(prev => prev.filter(id => id !== change.id));
        }
      }
    });
  }, [updateBookmark]);

  const onSelectionChange = useCallback(({ nodes: selectedNodes }: { nodes: Node[] }) => {
    setSelectedNodes(selectedNodes.map(node => node.id));
  }, []);

  const onEdgesChange = useCallback(() => {
    // Handle edge changes if needed  
  }, []);

  // Add selection state and connection handlers to nodes
  const nodesWithSelection = nodes.map(node => ({
    ...node,
    selected: selectedNodes.includes(node.id),
    data: {
      ...node.data,
      onConnectionStart: (nodeId: string, event: React.MouseEvent) => {
        if (event.button === 2) { // Right click
          event.preventDefault();
          event.stopPropagation();
          setConnectingFrom(nodeId);
          setIsDragging(true);
          
          // Get the center of the actual node element
          const nodeElement = (event.target as HTMLElement).closest('.bookmark-node');
          if (nodeElement) {
            const rect = nodeElement.getBoundingClientRect();
            connectionStartRef.current = { 
              x: rect.left + rect.width / 2, 
              y: rect.top + rect.height / 2 
            };
          } else {
            // Fallback to click position
            connectionStartRef.current = { 
              x: event.clientX, 
              y: event.clientY 
            };
          }
          
          // Show the connection line
          if (connectionLineRef.current && connectionStartRef.current) {
            connectionLineRef.current.style.display = 'block';
            connectionLineRef.current.setAttribute('x1', String(connectionStartRef.current.x));
            connectionLineRef.current.setAttribute('y1', String(connectionStartRef.current.y));
            connectionLineRef.current.setAttribute('x2', String(event.clientX));
            connectionLineRef.current.setAttribute('y2', String(event.clientY));
          }
        }
      },
      onConnectionEnd: (nodeId: string) => {
        // This is now handled in the global handleMouseUp
        console.log('onConnectionEnd called for nodeId:', nodeId);
      }
    }
  }));

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (connectingFrom && connectionStartRef.current && isDragging && connectionLineRef.current) {
      // Directly update SVG attributes - no state updates, no re-renders
      connectionLineRef.current.setAttribute('x2', String(e.clientX));
      connectionLineRef.current.setAttribute('y2', String(e.clientY));
    }
  }, [connectingFrom, isDragging]);

  const handleMouseUp = useCallback((e: React.MouseEvent) => {
    if (connectingFrom && isDragging) {
      // Find what element is under the mouse
      const elementUnderMouse = document.elementFromPoint(e.clientX, e.clientY);
      const nodeElement = elementUnderMouse?.closest('[data-id]');
      const targetNodeId = nodeElement?.getAttribute('data-id');
      
      console.log('Mouse up - elementUnderMouse:', elementUnderMouse);
      console.log('Mouse up - nodeElement:', nodeElement);
      console.log('Mouse up - targetNodeId:', targetNodeId);
      
      if (targetNodeId && targetNodeId !== connectingFrom) {
        // Check if connection already exists
        const existingConnection = edges.find(edge => 
          (edge.source === connectingFrom && edge.target === targetNodeId) ||
          (edge.source === targetNodeId && edge.target === connectingFrom)
        );

        if (existingConnection) {
          console.log('Removing existing connection between:', connectingFrom, targetNodeId);
          removeConnectionBetween(connectingFrom, targetNodeId);
        } else {
          console.log('Adding new connection:', connectingFrom, '->', targetNodeId);
          addConnection({
            source: connectingFrom,
            target: targetNodeId,
            type: 'default',
          });
        }
      }
    }
    
    setConnectingFrom(null);
    setIsDragging(false);
    connectionStartRef.current = null;
    
    // Hide the connection line
    if (connectionLineRef.current) {
      connectionLineRef.current.style.display = 'none';
    }
  }, [connectingFrom, isDragging, edges, addConnection, removeConnectionBetween]);

  const handleCanvasClick = useCallback((e: React.MouseEvent) => {
    // Close any open context menus when clicking on canvas
    const event = new CustomEvent('closeContextMenus');
    document.dispatchEvent(event);
  }, []);


  return (
    <div 
      className="canvas-container"
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onClick={handleCanvasClick}
      onContextMenu={(e) => e.preventDefault()}
    >
      <ReactFlow
        nodes={nodesWithSelection}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onNodeDragStop={onNodeDragStop}
        onSelectionChange={onSelectionChange}
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

      {/* Connection line preview - rendered as portal to avoid React Flow transforms */}
      {createPortal(
        <svg
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            width: '100vw',
            height: '100vh',
            pointerEvents: 'none',
            zIndex: 1000,
          }}
        >
          <line
            ref={connectionLineRef}
            x1={0}
            y1={0}
            x2={0}
            y2={0}
            stroke="#0078d4"
            strokeWidth={2}
            strokeDasharray="4 4"
            style={{ display: 'none' }}
          />
        </svg>,
        document.body
      )}
    </div>
  );
};