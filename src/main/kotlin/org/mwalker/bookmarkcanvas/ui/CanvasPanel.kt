package org.mwalker.bookmarkcanvas.ui

import com.intellij.openapi.diagnostic.Logger
import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.model.CanvasState
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import java.awt.*
import java.awt.event.*
import javax.swing.*

/**
 * Main canvas panel for displaying and interacting with bookmark nodes
 */
class CanvasPanel(val project: Project) : JPanel() {
    val canvasState: CanvasState = CanvasPersistenceService.getInstance().getCanvasState(project)
    val nodeComponents = mutableMapOf<String, NodeComponent>()
    val selectedNodes = mutableSetOf<NodeComponent>()
    var connectionStartNode: NodeComponent? = null
    var dragStartPoint: Point? = null
    var isPanning = false
    var isDrawingSelectionBox = false
    var selectionStart: Point? = null
    var selectionEnd: Point? = null
    var tempConnectionEndPoint: Point? = null
    var _zoomFactor = 1.0
    val zoomFactor: Double get() = _zoomFactor
    // Delegate grid properties to canvas state
    private var _snapToGrid: Boolean = false
    private var _showGrid: Boolean = false
    
    // Grid properties
    val GRID_SIZE = 20
    
    // Helper managers
    private val nodeManager: CanvasNodeManager
    private val selectionManager: CanvasSelectionManager
    private val connectionManager: CanvasConnectionManager
    private val zoomManager: CanvasZoomManager
    private val contextMenuManager: CanvasContextMenuManager
    private val eventHandler: CanvasEventHandler
    
    companion object {
        private val LOG = Logger.getInstance(NodeComponent::class.java)

        // UI Colors
        val CANVAS_BACKGROUND = JBColor(
            Color(240, 240, 240), // Light mode
            Color(30, 30, 30) // Dark mode
        )
        val GRID_COLOR = JBColor(
            Color(210, 210, 210), // Light mode
            Color(50, 50, 50) // Dark mode
        )
        val SELECTION_BOX_COLOR = JBColor(
            Color(100, 150, 255, 50), // Light mode with transparency
            Color(80, 120, 200, 50) // Dark mode with transparency
        )
        val SELECTION_BOX_BORDER_COLOR = JBColor(
            Color(70, 130, 230), // Light mode
            Color(100, 150, 230) // Dark mode
        )
    }

    init {
        // Initialize grid settings from canvas state
        _snapToGrid = canvasState.snapToGrid
        _showGrid = canvasState.showGrid
        _zoomFactor = canvasState.zoomFactor
        
        layout = null // Free positioning
        background = CANVAS_BACKGROUND

        // Initialize managers
        nodeManager = CanvasNodeManager(this, project)
        selectionManager = CanvasSelectionManager(this)
        connectionManager = CanvasConnectionManager(this, project)
        zoomManager = CanvasZoomManager(this, nodeComponents)
        contextMenuManager = CanvasContextMenuManager(this, project)
        eventHandler = CanvasEventHandler(this, project)
        
        // Set up the canvas
        initializeNodes()
        eventHandler.setupEventListeners()

        // Initial size - large to allow unlimited panning
        preferredSize = Dimension(5000, 5000)
        
        // Restore scroll position after initialization
        SwingUtilities.invokeLater {
            val scrollPane = parent?.parent as? JScrollPane
            scrollPane?.viewport?.viewPosition = Point(canvasState.scrollPositionX, canvasState.scrollPositionY)
        }
    }

    private fun initializeNodes() {
        canvasState.nodes.values.forEach { node ->
            nodeManager.addNodeComponent(node)
        }
    }

    fun addNodeComponent(node: BookmarkNode) {
        nodeManager.addNodeComponent(node)
    }
    
    fun refreshFromState() {
        // First clear existing node components
        removeAll()
        nodeComponents.clear()
        selectedNodes.clear()
        connectionStartNode = null
        tempConnectionEndPoint = null
        
        // Recreate node components from canvas state
        for (node in canvasState.nodes.values) {
            nodeManager.addNodeComponent(node)
        }
        
        // Request repaint to reflect all changes
        revalidate()
        repaint()
    }

    fun clearSelection() {
        selectionManager.clearSelection()
    }
    
    fun finalizeSelection() {
        selectionManager.finalizeSelection()
    }
    
    fun getSelectionRectangle(): Rectangle {
        return selectionManager.getSelectionRectangle()
    }

    fun createNewConnection(source: BookmarkNode, target: BookmarkNode) {
        connectionManager.createNewConnection(source, target)
    }

    fun showCanvasContextMenu(point: Point) {
        contextMenuManager.showCanvasContextMenu(point)
    }
    
    fun showAddBookmarkDialog(point: Point) {
        contextMenuManager.showAddBookmarkDialog(point)
    }

    fun refreshBookmarks() {
        contextMenuManager.refreshBookmarks()
    }

    fun clearCanvas() {
        removeAll()
        nodeComponents.clear()
        selectedNodes.clear()
        canvasState.nodes.clear()
        canvasState.connections.clear()
        CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)
        repaint()
    }

    fun zoomIn() {
        zoomManager.zoomIn()
    }

    fun zoomOut() {
        zoomManager.zoomOut()
    }

    fun updateCanvasSize() {
        zoomManager.updateCanvasSize()
    }

    fun goToTopLeftNode() {
        zoomManager.goToTopLeftNode()
    }

    // Property accessors 
    val snapToGrid: Boolean
        get() = _snapToGrid
    
    val showGrid: Boolean
        get() = _showGrid
    
    fun setSnapToGrid(value: Boolean) {
        _snapToGrid = value
        _showGrid = value // Show grid when snap is enabled
        
        // Update canvas state
        canvasState.setGridPreferences(_snapToGrid, _showGrid)
        
        // Persist the state
        CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)
        
        if (_snapToGrid) {
            // Snap all existing nodes to grid
            for (nodeComp in nodeComponents.values) {
                val location = nodeComp.location
                val x = (location.x / GRID_SIZE).toInt() * GRID_SIZE
                val y = (location.y / GRID_SIZE).toInt() * GRID_SIZE
                nodeComp.setLocation(x, y)
                val node = nodeComp.node
                node.positionX = (x / zoomFactor).toInt()
                node.positionY = (y / zoomFactor).toInt()
            }
        }
        repaint()
    }
    
    fun setShowGrid(value: Boolean) {
        _showGrid = value
        
        // Update canvas state
        canvasState.setGridPreferences(_snapToGrid, _showGrid)
        
        // Persist the state
        CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)
        
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g.create() as Graphics2D

        // Set rendering hints for smoother lines
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Draw grid if enabled
        if (showGrid) {
            drawGrid(g2d)
        }

        // Draw connections between nodes
        for (connection in canvasState.connections) {
            val source = nodeComponents[connection.sourceNodeId]
            val target = nodeComponents[connection.targetNodeId]

            if (source != null && target != null) {
                connectionManager.drawConnection(g2d, source, target, connection.color)
            }
        }

        // Draw temporary connection if creating one
        if (connectionStartNode != null && tempConnectionEndPoint != null) {
            connectionManager.drawTemporaryConnection(g2d, connectionStartNode!!, tempConnectionEndPoint!!)
        }

        g2d.dispose()
    }
    
    private fun drawGrid(g2d: Graphics2D) {
        g2d.color = GRID_COLOR
        val scaledGridSize = (GRID_SIZE * zoomFactor).toInt()

        // Draw vertical lines
        var x = 0
        while (x < width) {
            g2d.drawLine(x, 0, x, height)
            x += scaledGridSize
        }

        // Draw horizontal lines
        var y = 0
        while (y < height) {
            g2d.drawLine(0, y, width, y)
            y += scaledGridSize
        }
    }
    
    // Paint the selection box in the glass pane layer to ensure it's on top
    override fun paint(g: Graphics) {
        super.paint(g)
        
        // After painting everything else, draw the selection box on top if active
        if (isDrawingSelectionBox && selectionStart != null && selectionEnd != null) {
            selectionManager.drawSelectionBox(g)
        }
    }
}
