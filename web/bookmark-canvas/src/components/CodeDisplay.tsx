import { memo, useEffect, useRef } from 'react';
import Prism from 'prismjs';
import 'prismjs/themes/prism-dark.css';
import 'prismjs/components/prism-kotlin';

interface CodeDisplayProps {
  code: string;
  language: string;
  width: string;
  height: string;
}

export const CodeDisplay = memo(({ code, language, width, height }: CodeDisplayProps) => {
  const codeRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (codeRef.current) {
      Prism.highlightElement(codeRef.current);
    }
  }, [code, language]);

  return (
    <div 
      className="code-display-container"
      style={{ 
        width, 
        height, 
        overflow: 'hidden',
        background: '#1e1e1e',
        fontSize: '12px',
        fontFamily: 'Consolas, Monaco, monospace',
      }}
    >
      <pre className="line-numbers" style={{ margin: 0, padding: '8px', height: '100%', boxSizing: 'border-box', overflow: 'hidden' }}>
        <code ref={codeRef} className={`language-${language}`}>
          {code}
        </code>
      </pre>
    </div>
  );
});

CodeDisplay.displayName = 'CodeDisplay';