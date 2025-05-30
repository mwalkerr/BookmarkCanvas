import { useEffect } from 'react';
import { Canvas } from './components/Canvas';
import { useCanvasStore } from './store/canvasStore';
import './App.css';

function App() {
  const { addBookmark, clearCanvas } = useCanvasStore();

  // Add sample bookmarks for testing
  useEffect(() => {
    // Clear any existing nodes first, then add sample data
    clearCanvas();
      addBookmark({
        type: 'bookmark',
        position: { x: 100, y: 100 },
        data: {
          id: '',
          title: 'CanvasEventHandler.kt',
          content: `LOG.info("Mouse released on canvas, \${e.point}, \${e.button}, clickCount: \${e.clickCount}")
"isPopupTrigger: \${e.isPopupTrigger}, isLeft: \${SwingUtilities.isLeftMouseButton(e)}"
"connectionStartNode is null: \${canvasPanel.connectionStartNode == null}"
"component at point: \${canvasPanel.getComponentAt(e.point)}")

// Clear any pending throttled actions
pendingThrottledActions.clear()`,
          language: 'kotlin',
          filePath: 'CanvasEventHandler.kt:117',
        },
      });

      addBookmark({
        type: 'bookmark',
        position: { x: 500, y: 200 },
        data: {
          id: '',
          title: 'HomeAction.kt',
          content: `class HomeAction(private val canvasPanel: CanvasPanel) : AnAction("Go to Home", "Go to Home", null) {

    override fun actionPerformed(e: AnActionEvent) {
        // Find the top-left most node and recenter on it
        val topLeftNode = canvasPanel.children`,
          language: 'kotlin',
          filePath: 'HomeAction.kt:10',
        },
      });

      addBookmark({
        type: 'bookmark',
        position: { x: 300, y: 400 },
        data: {
          id: '',
          title: 'AddFileToCanvasWebAction.kt',
          content: `class AddFileToCanvasWebAction(
    private val project: Project,
    private val canvasPanel: CanvasInterface
) : AnAction("Add File to Canvas", "Add current file to canvas", null)`,
          language: 'kotlin',
          filePath: 'AddFileToCanvasWebAction.kt:11',
        },
      });
  }, [addBookmark, clearCanvas]);

  return (
    <div className="app">
      <Canvas />
    </div>
  );
}

export default App
