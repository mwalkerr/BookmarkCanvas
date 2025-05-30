import { useCallback, useState, useRef } from 'react';
import { createPortal } from 'react-dom';
import ReactFlow, {
  Background,
  Controls,
  MiniMap,
  ReactFlowProvider,
  useReactFlow,
} from 'reactflow';
import type { Connection, Node } from 'reactflow';
import 'reactflow/dist/style.css';

import { BookmarkNode } from './BookmarkNode';
import { useCanvasStore } from '../store/canvasStore';

const nodeTypes = {
  bookmark: BookmarkNode,
};

const CanvasInner = () => {
  const {
    nodes,
    edges,
    isGridVisible,
    addConnection,
    removeConnectionBetween,
    updateBookmark,
    recalculateEdges,
  } = useCanvasStore();

  const { screenToFlowPosition } = useReactFlow();

  const [selectedNodes, setSelectedNodes] = useState<string[]>([]);
  
  const [connectingFrom, setConnectingFrom] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [isSelecting, setIsSelecting] = useState(false);
  
  // Use refs for selection box to avoid re-renders
  const selectionBoxRef = useRef<HTMLDivElement>(null);
  const selectionStartRef = useRef<{ x: number; y: number } | null>(null);
  
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
      recalculateEdges(node.id);
    },
    [updateBookmark, recalculateEdges]
  );

  const onNodesChange = useCallback((changes: any) => {
    const movedNodes = new Set<string>();
    
    // Handle position changes during drag for visual feedback
    changes.forEach((change: any) => {
      if (change.type === 'position' && change.dragging) {
        updateBookmark(change.id, { position: change.position });
      }
      if (change.type === 'position' && !change.dragging) {
        // Node drag finished - track it for edge recalculation
        movedNodes.add(change.id);
      }
      if (change.type === 'select') {
        if (change.selected) {
          setSelectedNodes(prev => [...prev.filter(id => id !== change.id), change.id]);
        } else {
          setSelectedNodes(prev => prev.filter(id => id !== change.id));
        }
      }
    });
    
    // Recalculate edges for all moved nodes
    movedNodes.forEach(nodeId => {
      recalculateEdges(nodeId);
    });
  }, [updateBookmark, recalculateEdges]);

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
    
    if (isSelecting && selectionStartRef.current && selectionBoxRef.current) {
      // Directly update selection box DOM - no state updates, no re-renders
      const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
      const endX = e.clientX - rect.left;
      const endY = e.clientY - rect.top;
      
      const startX = selectionStartRef.current.x;
      const startY = selectionStartRef.current.y;
      
      const left = Math.min(startX, endX);
      const top = Math.min(startY, endY);
      const width = Math.abs(endX - startX);
      const height = Math.abs(endY - startY);
      
      selectionBoxRef.current.style.left = `${left}px`;
      selectionBoxRef.current.style.top = `${top}px`;
      selectionBoxRef.current.style.width = `${width}px`;
      selectionBoxRef.current.style.height = `${height}px`;
      selectionBoxRef.current.style.display = 'block';
    }
  }, [connectingFrom, isDragging, isSelecting]);

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
    
    if (isSelecting && selectionStartRef.current && selectionBoxRef.current) {
      // Finish selection - find nodes within selection box
      const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
      const endX = e.clientX - rect.left;
      const endY = e.clientY - rect.top;
      
      const startX = selectionStartRef.current.x;
      const startY = selectionStartRef.current.y;
      
      const minX = Math.min(startX, endX);
      const maxX = Math.max(startX, endX);
      const minY = Math.min(startY, endY);
      const maxY = Math.max(startY, endY);
      
      // Only select if the selection box has some size
      if (Math.abs(endX - startX) > 5 && Math.abs(endY - startY) > 5) {
        console.log('Selection box coordinates (DOM):', { minX, maxX, minY, maxY });
        
        // Convert DOM coordinates to React Flow coordinates
        const topLeft = screenToFlowPosition({ x: minX, y: minY });
        const bottomRight = screenToFlowPosition({ x: maxX, y: maxY });
        
        const flowMinX = topLeft.x;
        const flowMinY = topLeft.y;
        const flowMaxX = bottomRight.x;
        const flowMaxY = bottomRight.y;
        
        console.log('Selection box coordinates (Flow):', { 
          flowMinX, flowMaxX, flowMinY, flowMaxY 
        });
        
        const nodesInSelection = nodes.filter(node => {
          const nodeX = node.position.x;
          const nodeY = node.position.y;
          const nodeWidth = 350; // Default node width
          const nodeHeight = 250; // Default node height
          
          console.log(`Node ${node.id} at (${nodeX}, ${nodeY}) to (${nodeX + nodeWidth}, ${nodeY + nodeHeight})`);
          
          // Check if any part of the node intersects with selection box in Flow coordinates
          const nodeRight = nodeX + nodeWidth;
          const nodeBottom = nodeY + nodeHeight;
          
          const overlaps = !(nodeRight < flowMinX || nodeX > flowMaxX || 
                           nodeBottom < flowMinY || nodeY > flowMaxY);
          
          console.log(`Node ${node.id} overlaps:`, overlaps);
          return overlaps;
        });
        
        const selectedNodeIds = nodesInSelection.map(node => node.id);
        setSelectedNodes(selectedNodeIds);
        
        console.log('Selected nodes:', selectedNodeIds.length, selectedNodeIds);
      } else {
        console.log('Selection box too small:', Math.abs(endX - startX), Math.abs(endY - startY));
      }
      
      // Hide selection box
      selectionBoxRef.current.style.display = 'none';
    }
    
    // Reset states
    setConnectingFrom(null);
    setIsDragging(false);
    setIsSelecting(false);
    connectionStartRef.current = null;
    selectionStartRef.current = null;
    
    // Hide the connection line
    if (connectionLineRef.current) {
      connectionLineRef.current.style.display = 'none';
    }
  }, [connectingFrom, isDragging, isSelecting, edges, addConnection, removeConnectionBetween, nodes]);

  const handleCanvasMouseDown = useCallback((e: React.MouseEvent) => {
    // Check if right-clicking on empty canvas (not on a node)
    if (e.button === 2) {
      const target = e.target as HTMLElement;
      const isOnNode = target.closest('.bookmark-node');
      
      if (!isOnNode) {
        // Start selection box
        setIsSelecting(true);
        const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
        const startX = e.clientX - rect.left;
        const startY = e.clientY - rect.top;
        
        selectionStartRef.current = { x: startX, y: startY };
        
        // Initialize selection box
        if (selectionBoxRef.current) {
          selectionBoxRef.current.style.left = `${startX}px`;
          selectionBoxRef.current.style.top = `${startY}px`;
          selectionBoxRef.current.style.width = '0px';
          selectionBoxRef.current.style.height = '0px';
          selectionBoxRef.current.style.display = 'block';
        }
      }
    }
  }, []);

  const handleCanvasClick = useCallback(() => {
    // Close any open context menus when clicking on canvas
    const event = new CustomEvent('closeContextMenus');
    document.dispatchEvent(event);
  }, []);



  return (
    <div 
      className="canvas-container"
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onMouseDown={handleCanvasMouseDown}
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
        multiSelectionKeyCode={null} // Allow multi-selection without key
        selectionKeyCode={null} // Allow selection without key
        deleteKeyCode={null} // Disable delete key
        fitView
        attributionPosition="top-right"
      >
        <Background
          gap={20}
          size={1}
          style={{ opacity: isGridVisible ? 0.5 : 0 }}
        />
        <Controls />
        <MiniMap />
      </ReactFlow>

      {/* Selection box */}
      <div 
        ref={selectionBoxRef}
        style={{
          position: 'absolute',
          border: '1px dashed #0078d4',
          backgroundColor: 'rgba(0, 120, 212, 0.1)',
          pointerEvents: 'none',
          zIndex: 500,
          display: 'none',
        }}
      />

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

export const Canvas = () => {
  return (
    <ReactFlowProvider>
      <CanvasInner />
    </ReactFlowProvider>
  );
};

