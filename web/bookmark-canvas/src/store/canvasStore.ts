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
  setSelectedNodes: (nodeIds: string[]) => void;
  toggleGrid: () => void;
  clearCanvas: () => void;
}

const generateId = () => Math.random().toString(36).substr(2, 9);

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
      const newEdge: BookmarkConnection = {
        ...connection,
        id: generateId(),
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