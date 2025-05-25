import { memo } from 'react';
import { Handle, Position } from 'reactflow';
import { Editor } from '@monaco-editor/react';
import type { NodeProps } from 'reactflow';

type BookmarkData = {
  id: string;
  title: string;
  content: string;
  language: string;
  filePath?: string;
};

interface BookmarkNodeProps extends NodeProps {
  data: BookmarkData;
}

export const BookmarkNode = memo(({ data, selected }: BookmarkNodeProps) => {
  return (
    <div className={`bookmark-node ${selected ? 'selected' : ''}`}>
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
      <div className="bookmark-content">
        <Editor
          height="200px"
          width="300px"
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
          }}
        />
      </div>
    </div>
  );
});

BookmarkNode.displayName = 'BookmarkNode';