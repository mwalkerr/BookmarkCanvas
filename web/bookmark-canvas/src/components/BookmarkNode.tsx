import { memo, useState, useCallback, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { Handle, Position } from 'reactflow';
import { Editor } from '@monaco-editor/react';
import { CodeDisplay } from './CodeDisplay';
import { Rnd } from 'react-rnd';
import type { NodeProps } from 'reactflow';

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
  const [useCodeDisplay, setUseCodeDisplay] = useState(true); // Use Prism instead of Monaco for now
  const [size, setSize] = useState({ width: 350, height: 250 });
  const [isResizing, setIsResizing] = useState(false);
  const [resizeStart, setResizeStart] = useState({ x: 0, y: 0, width: 0, height: 0 });
  const [rightClickStart, setRightClickStart] = useState<{ x: number; y: number } | null>(null);

  const handleContextMenu = useCallback((e: React.MouseEvent) => {
    e.preventDefault(); // Prevent default context menu
    e.stopPropagation();
  }, []);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    if (e.button === 2) { // Right click
      setRightClickStart({ x: e.clientX, y: e.clientY });
      if (data.onConnectionStart) {
        data.onConnectionStart(id, e);
      }
    }
  }, [data, id]);

  const handleMouseUp = useCallback((e: React.MouseEvent) => {
    if (e.button === 2 && rightClickStart) { // Right click
      const distance = Math.sqrt(
        Math.pow(e.clientX - rightClickStart.x, 2) + 
        Math.pow(e.clientY - rightClickStart.y, 2)
      );
      
      if (distance < 5) {
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
    setIsResizing(true);
    
    const startData = {
      x: e.clientX,
      y: e.clientY,
      width: size.width,
      height: size.height,
    };
    
    setResizeStart(startData);

    const handleMouseMove = (moveEvent: MouseEvent) => {
      moveEvent.preventDefault();
      moveEvent.stopPropagation();
      
      const deltaX = moveEvent.clientX - startData.x;
      const deltaY = moveEvent.clientY - startData.y;

      const newWidth = Math.max(250, startData.width + deltaX);
      const newHeight = Math.max(150, startData.height + deltaY);

      setSize({ width: newWidth, height: newHeight });
    };

    const handleMouseUp = (upEvent: MouseEvent) => {
      upEvent.preventDefault();
      upEvent.stopPropagation();
      setIsResizing(false);
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
          type="target"
          position={Position.Left}
          style={{ opacity: 0 }}
        />
        <Handle
          type="source"
          position={Position.Right}
          style={{ opacity: 0 }}
        />
        
        {/* Header */}
        <div className="bookmark-header">
          <span className="bookmark-title">{data.title}</span>
          {data.filePath && (
            <span className="bookmark-path">{data.filePath}</span>
          )}
        </div>
        
        {/* Code content */}
        <div className="bookmark-content" style={{ height: `${size.height - 60}px` }}>
          {useCodeDisplay ? (
            <CodeDisplay 
              code={data.content}
              language={data.language}
              width={`${size.width - 2}px`}
              height={`${size.height - 60}px`}
            />
          ) : (
            <Editor
              height={`${size.height - 60}px`}
              width={`${size.width - 2}px`}
              language={data.language}
              value={data.content}
              theme="vs-dark"
              options={{
                readOnly: true,
                minimap: { enabled: false },
                scrollBeyondLastLine: false,
                fontSize: 12,
                lineNumbers: 'on',
                folding: false,
                wordWrap: 'on',
                selectOnLineNumbers: false,
                selectionHighlight: false,
                occurrencesHighlight: false,
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
            bottom: -1,
            right: -1,
            width: 16,
            height: 16,
            cursor: 'se-resize',
            zIndex: 1000, // Higher z-index
            background: '#2a2a2a',
            border: '1px solid #404040',
            borderRadius: '0 0 8px 0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <svg width="8" height="8" viewBox="0 0 8 8" style={{ pointerEvents: 'none' }}>
            <g stroke="#888" strokeWidth="1" opacity="0.8">
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
              zIndex: 999,
            }}
          />
          <div 
            className="context-menu"
            style={{ 
              position: 'fixed', 
              top: contextMenu.y, 
              left: contextMenu.x,
              zIndex: 1000
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