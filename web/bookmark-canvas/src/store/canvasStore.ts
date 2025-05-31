import { create } from 'zustand';
import { subscribeWithSelector } from 'zustand/middleware';

type BookmarkData = {
  id: string;
  title: string;
  content: string;
  language: string;
  filePath?: string;
};

type BookmarkNode = {
  id: string;
  type: 'bookmark';
  position: { x: number; y: number };
  data: BookmarkData;
  width?: number;
  height?: number;
};

type BookmarkConnection = {
  id: string;
  source: string;
  target: string;
  type?: 'default' | 'straight' | 'step' | 'smoothstep';
  sourceHandle?: string;
  targetHandle?: string;
};

type CanvasState = {
  nodes: BookmarkNode[];
  edges: BookmarkConnection[];
  selectedNodes: string[];
  isGridVisible: boolean;
};

interface CanvasStore extends CanvasState {
  // Actions
  addBookmark: (bookmark: Omit<BookmarkNode, 'id'>) => void;
  updateBookmark: (id: string, updates: Partial<BookmarkNode>) => void;
  removeBookmark: (id: string) => void;
  addConnection: (connection: Omit<BookmarkConnection, 'id'>) => void;
  removeConnection: (id: string) => void;
  removeConnectionBetween: (sourceId: string, targetId: string) => void;
  recalculateEdges: (nodeId: string) => void;
  setSelectedNodes: (nodeIds: string[]) => void;
  toggleGrid: () => void;
  clearCanvas: () => void;
}

const generateId = () => Math.random().toString(36).substr(2, 9);

// These defaults are used for calculating connection points.
// If nodes have variable sizes stored, those should be used instead.
const DEFAULT_NODE_WIDTH_FOR_HANDLES = 350;
const DEFAULT_NODE_HEIGHT_FOR_HANDLES = 250;

const calculateConnectionHandles = (sourceNode: BookmarkNode, targetNode: BookmarkNode) => {
  const sourceCenter = {
    x: sourceNode.position.x + (sourceNode.width || DEFAULT_NODE_WIDTH_FOR_HANDLES) / 2,
    y: sourceNode.position.y + (sourceNode.height || DEFAULT_NODE_HEIGHT_FOR_HANDLES) / 2
  };
  
  const targetCenter = {
    x: targetNode.position.x + (targetNode.width || DEFAULT_NODE_WIDTH_FOR_HANDLES) / 2,
    y: targetNode.position.y + (targetNode.height || DEFAULT_NODE_HEIGHT_FOR_HANDLES) / 2
  };
  
  // Determine connection points based on relative positions
  let sourceHandle = '';
  let targetHandle = '';
  
  const dx = targetCenter.x - sourceCenter.x;
  const dy = targetCenter.y - sourceCenter.y;
  
  if (Math.abs(dx) > Math.abs(dy)) {
    // Horizontal connection is stronger
    if (dx > 0) {
      sourceHandle = 'right';
      targetHandle = 'left';
    } else {
      sourceHandle = 'left';
      targetHandle = 'right';
    }
  } else {
    // Vertical connection is stronger
    if (dy > 0) {
      sourceHandle = 'bottom';
      targetHandle = 'top';
    } else {
      sourceHandle = 'top';
      targetHandle = 'bottom';
    }
  }
  
  return { sourceHandle, targetHandle };
};

export const useCanvasStore = create<CanvasStore>()(
  subscribeWithSelector((set, get) => ({
    // Initial state
    nodes: [],
    edges: [],
    selectedNodes: [],
    isGridVisible: true,

    // Actions
    addBookmark: (bookmark) => {
      const newNode: BookmarkNode = {
        ...bookmark,
        id: generateId(),
      };
      set((state) => ({
        nodes: [...state.nodes, newNode],
      }));
    },

    updateBookmark: (id, updates) => {
      set((state) => ({
        nodes: state.nodes.map((node) =>
          node.id === id ? { ...node, ...updates } : node
        ),
      }));
    },

    removeBookmark: (id) => {
      set((state) => ({
        nodes: state.nodes.filter((node) => node.id !== id),
        edges: state.edges.filter((edge) => edge.source !== id && edge.target !== id),
        selectedNodes: state.selectedNodes.filter((nodeId) => nodeId !== id),
      }));
    },

    addConnection: (connection) => {
      const state = get();
      const sourceNode = state.nodes.find(n => n.id === connection.source);
      const targetNode = state.nodes.find(n => n.id === connection.target);
      
      if (!sourceNode || !targetNode) return;
      
      const { sourceHandle, targetHandle } = calculateConnectionHandles(sourceNode, targetNode);
      
      const newEdge: BookmarkConnection = {
        ...connection,
        id: generateId(),
        sourceHandle,
        targetHandle,
      };
      
      set((state) => ({
        edges: [...state.edges, newEdge],
      }));
    },

    removeConnection: (id) => {
      set((state) => ({
        edges: state.edges.filter((edge) => edge.id !== id),
      }));
    },

    removeConnectionBetween: (sourceId, targetId) => {
      set((state) => ({
        edges: state.edges.filter((edge) => 
          !((edge.source === sourceId && edge.target === targetId) ||
            (edge.source === targetId && edge.target === sourceId))
        ),
      }));
    },

    recalculateEdges: (nodeId) => {
      const state = get();
      const affectedEdges = state.edges.filter(edge => 
        edge.source === nodeId || edge.target === nodeId
      );
      
      if (affectedEdges.length === 0) return;
      
      const updatedEdges = state.edges.map(edge => {
        if (edge.source === nodeId || edge.target === nodeId) {
          const sourceNode = state.nodes.find(n => n.id === edge.source);
          const targetNode = state.nodes.find(n => n.id === edge.target);
          
          if (sourceNode && targetNode) {
            const { sourceHandle, targetHandle } = calculateConnectionHandles(sourceNode, targetNode);
            return {
              ...edge,
              sourceHandle,
              targetHandle,
            };
          }
        }
        return edge;
      });
      
      set({ edges: updatedEdges });
    },

    setSelectedNodes: (nodeIds) => {
      set({ selectedNodes: nodeIds });
    },

    toggleGrid: () => {
      set((state) => ({ isGridVisible: !state.isGridVisible }));
    },

    clearCanvas: () => {
      set({
        nodes: [],
        edges: [],
        selectedNodes: [],
      });
    },
  }))
);
