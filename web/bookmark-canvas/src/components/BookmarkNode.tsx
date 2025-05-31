import { memo, useState, useCallback, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { Handle, Position } from 'reactflow';
import { Editor } from '@monaco-editor/react';
import { CodeDisplay } from './CodeDisplay';
// import { Rnd } from 'react-rnd';
import type { NodeProps } from 'reactflow';

const INITIAL_NODE_WIDTH = 350;
const INITIAL_NODE_HEIGHT = 250;
const MIN_NODE_WIDTH = 250;
const MIN_NODE_HEIGHT = 150;

const RIGHT_CLICK_BUTTON_CODE = 2;
const CLICK_DRAG_THRESHOLD_PX = 5; // Pixels to differentiate click from drag

const HANDLE_OPACITY = 0;
const HANDLE_OFFSET = -1; // For positioning handles slightly outside the node

const NODE_HEADER_HEIGHT = 60; // Includes title and path
const NODE_CONTENT_PADDING = 2; // Padding around the content area (e.g., editor)

const EDITOR_FONT_SIZE = 12;
const EDITOR_MINIMAP_ENABLED = false;
const EDITOR_SCROLL_BEYOND_LAST_LINE = false;
const EDITOR_LINE_NUMBERS_ON = 'on';
const EDITOR_FOLDING_ENABLED = false;
const EDITOR_WORD_WRAP_ON = 'on';

const RESIZE_HANDLE_SIZE = 16;
const RESIZE_HANDLE_OFFSET = -1;
const RESIZE_HANDLE_Z_INDEX = 1000;
const RESIZE_HANDLE_BORDER_RADIUS = '8px'; // For the bottom-right corner
const RESIZE_HANDLE_SVG_SIZE = 8;
const RESIZE_HANDLE_SVG_STROKE_WIDTH = "1";
const RESIZE_HANDLE_SVG_OPACITY = "0.8";

const CONTEXT_MENU_OVERLAY_Z_INDEX = 999;
const CONTEXT_MENU_Z_INDEX = 1000;

type BookmarkData = {
  id: string;
  title: string;
  content: string;
  language: string;
  filePath?: string;
  onConnectionStart?: (nodeId: string, event: React.MouseEvent) => void;
  onConnectionEnd?: (nodeId: string) => void;
};

interface BookmarkNodeProps extends NodeProps {
  data: BookmarkData;
}

export const BookmarkNode = memo(({ data, selected, id }: BookmarkNodeProps) => {
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number } | null>(null);
  const [useCodeDisplay] = useState(true); // Use Prism instead of Monaco for now
  const [size, setSize] = useState({ width: INITIAL_NODE_WIDTH, height: INITIAL_NODE_HEIGHT });
  // const [isResizing, setIsResizing] = useState(false);
  // const [resizeStart, setResizeStart] = useState({ x: 0, y: 0, width: 0, height: 0 });
  const [rightClickStart, setRightClickStart] = useState<{ x: number; y: number } | null>(null);

  const handleContextMenu = useCallback((e: React.MouseEvent) => {
    e.preventDefault(); // Prevent default context menu
    e.stopPropagation();
  }, []);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    if (e.button === RIGHT_CLICK_BUTTON_CODE) { // Right click
      setRightClickStart({ x: e.clientX, y: e.clientY });
      if (data.onConnectionStart) {
        data.onConnectionStart(id, e);
      }
    }
  }, [data, id]);

  const handleMouseUp = useCallback((e: React.MouseEvent) => {
    if (e.button === RIGHT_CLICK_BUTTON_CODE && rightClickStart) { // Right click
      const distance = Math.sqrt(
        Math.pow(e.clientX - rightClickStart.x, 2) + 
        Math.pow(e.clientY - rightClickStart.y, 2)
      );
      
      if (distance < CLICK_DRAG_THRESHOLD_PX) {
        // It was a click, not a drag - show context menu
        setContextMenu({ 
          x: e.clientX, 
          y: e.clientY 
        });
      }
      
      setRightClickStart(null);
    }
  }, [rightClickStart]);

  const closeContextMenu = useCallback(() => {
    setContextMenu(null);
  }, []);

  // Listen for global context menu close events
  useEffect(() => {
    const handleCloseContextMenus = () => {
      setContextMenu(null);
    };
    
    document.addEventListener('closeContextMenus', handleCloseContextMenus);
    return () => {
      document.removeEventListener('closeContextMenus', handleCloseContextMenus);
    };
  }, []);

  const handleMenuAction = useCallback((action: string) => {
    console.log(`Action: ${action} on node ${data.title}`);
    closeContextMenu();
  }, [data.title, closeContextMenu]);

  const handleResizeStart = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    // setIsResizing(true);
    
    const startData = {
      x: e.clientX,
      y: e.clientY,
      width: size.width,
      height: size.height,
    };
    
    // setResizeStart(startData);

    const handleMouseMove = (moveEvent: MouseEvent) => {
      moveEvent.preventDefault();
      moveEvent.stopPropagation();
      
      const deltaX = moveEvent.clientX - startData.x;
      const deltaY = moveEvent.clientY - startData.y;

      const newWidth = Math.max(MIN_NODE_WIDTH, startData.width + deltaX);
      const newHeight = Math.max(MIN_NODE_HEIGHT, startData.height + deltaY);

      setSize({ width: newWidth, height: newHeight });
    };

    const handleMouseUp = (upEvent: MouseEvent) => {
      upEvent.preventDefault();
      upEvent.stopPropagation();
      // setIsResizing(false);
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
    };

    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
  }, [size]);



  return (
    <>
      <div 
        className={`bookmark-node ${selected ? 'selected' : ''}`}
        onContextMenu={handleContextMenu}
        onMouseDown={handleMouseDown}
        onMouseUp={handleMouseUp}
        style={{ width: size.width, height: size.height }}
      >
        {/* Connection handles - hidden but functional */}
        <Handle
          id="left"
          type="source"
          position={Position.Left}
          style={{ opacity: HANDLE_OPACITY, left: HANDLE_OFFSET }}
        />
        <Handle
          id="right"
          type="source"
          position={Position.Right}
          style={{ opacity: HANDLE_OPACITY, right: HANDLE_OFFSET }}
        />
        <Handle
          id="top"
          type="source"
          position={Position.Top}
          style={{ opacity: HANDLE_OPACITY, top: HANDLE_OFFSET }}
        />
        <Handle
          id="bottom"
          type="source"
          position={Position.Bottom}
          style={{ opacity: HANDLE_OPACITY, bottom: HANDLE_OFFSET }}
        />
        
        {/* Target handles */}
        <Handle
          id="left"
          type="target"
          position={Position.Left}
          style={{ opacity: HANDLE_OPACITY, left: HANDLE_OFFSET }}
        />
        <Handle
          id="right"
          type="target"
          position={Position.Right}
          style={{ opacity: HANDLE_OPACITY, right: HANDLE_OFFSET }}
        />
        <Handle
          id="top"
          type="target"
          position={Position.Top}
          style={{ opacity: HANDLE_OPACITY, top: HANDLE_OFFSET }}
        />
        <Handle
          id="bottom"
          type="target"
          position={Position.Bottom}
          style={{ opacity: HANDLE_OPACITY, bottom: HANDLE_OFFSET }}
        />
        
        {/* Header */}
        <div className="bookmark-header">
          <span className="bookmark-title">{data.title}</span>
          {data.filePath && (
            <span className="bookmark-path">{data.filePath}</span>
          )}
        </div>
        
        {/* Code content */}
        <div className="bookmark-content" style={{ height: `${size.height - NODE_HEADER_HEIGHT}px` }}>
          {useCodeDisplay ? (
            <CodeDisplay 
              code={data.content}
              language={data.language}
              width={`${size.width - NODE_CONTENT_PADDING}px`}
              height={`${size.height - NODE_HEADER_HEIGHT}px`}
            />
          ) : (
            <Editor
              height={`${size.height - NODE_HEADER_HEIGHT}px`}
              width={`${size.width - NODE_CONTENT_PADDING}px`}
              language={data.language}
              value={data.content}
              theme="vs-dark"
              options={{
                readOnly: true,
                minimap: { enabled: EDITOR_MINIMAP_ENABLED },
                scrollBeyondLastLine: EDITOR_SCROLL_BEYOND_LAST_LINE,
                fontSize: EDITOR_FONT_SIZE,
                lineNumbers: EDITOR_LINE_NUMBERS_ON,
                folding: EDITOR_FOLDING_ENABLED,
                wordWrap: EDITOR_WORD_WRAP_ON,
                selectOnLineNumbers: false,
                selectionHighlight: false,
                occurrencesHighlight: "off",
                domReadOnly: true,
                contextmenu: false,
                scrollbar: {
                  horizontal: 'hidden',
                  vertical: 'auto',
                },
                overviewRulerLanes: 0,
              }}
            />
          )}
        </div>

        {/* Single resize handle - always visible */}
        <div 
          className="resize-handle-corner nodrag"
          onMouseDown={(e) => {
            e.preventDefault();
            e.stopPropagation(); // Prevent node dragging
            handleResizeStart(e);
          }}
          style={{
            position: 'absolute',
            bottom: RESIZE_HANDLE_OFFSET,
            right: RESIZE_HANDLE_OFFSET,
            width: RESIZE_HANDLE_SIZE,
            height: RESIZE_HANDLE_SIZE,
            cursor: 'se-resize',
            zIndex: RESIZE_HANDLE_Z_INDEX, // Higher z-index
            background: '#2a2a2a',
            border: '1px solid #404040',
            borderRadius: `0 0 ${RESIZE_HANDLE_BORDER_RADIUS} 0`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <svg width={RESIZE_HANDLE_SVG_SIZE} height={RESIZE_HANDLE_SVG_SIZE} viewBox="0 0 8 8" style={{ pointerEvents: 'none' }}>
            <g stroke="#888" strokeWidth={RESIZE_HANDLE_SVG_STROKE_WIDTH} opacity={RESIZE_HANDLE_SVG_OPACITY}>
              <line x1="1" y1="7" x2="7" y2="1" />
              <line x1="3" y1="7" x2="7" y2="3" />
              <line x1="5" y1="7" x2="7" y2="5" />
            </g>
          </svg>
        </div>
      </div>

      {/* Context Menu - render as portal to escape React Flow transforms */}
      {contextMenu && createPortal(
        <>
          <div 
            className="context-menu-overlay" 
            onClick={closeContextMenu}
            style={{
              position: 'fixed',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              zIndex: CONTEXT_MENU_OVERLAY_Z_INDEX,
            }}
          />
          <div 
            className="context-menu"
            style={{ 
              position: 'fixed', 
              top: contextMenu.y, 
              left: contextMenu.x,
              zIndex: CONTEXT_MENU_Z_INDEX
            }}
          >
            <div className="context-menu-item" onClick={() => handleMenuAction('edit')}>
              Edit Source
            </div>
            <div className="context-menu-item" onClick={() => handleMenuAction('duplicate')}>
              Duplicate Node
            </div>
            <div className="context-menu-item" onClick={() => handleMenuAction('copy')}>
              Copy Content
            </div>
            <div className="context-menu-separator" />
            <div className="context-menu-item danger" onClick={() => handleMenuAction('remove')}>
              Remove Node
            </div>
          </div>
        </>,
        document.body
      )}
    </>
  );
});

BookmarkNode.displayName = 'BookmarkNode';
