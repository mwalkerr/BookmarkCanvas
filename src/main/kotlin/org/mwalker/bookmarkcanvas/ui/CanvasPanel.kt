package org.mwalker.bookmarkcanvas.ui

import com.intellij.openapi.diagnostic.Logger
import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.model.CanvasState
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import com.intellij.openapi.project.Project
import org.mwalker.bookmarkcanvas.ui.CanvasColors
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
    val zoomManager: CanvasZoomManager
    private val contextMenuManager: CanvasContextMenuManager
    private val eventHandler: CanvasEventHandler
    
    companion object {
        private val LOG = Logger.getInstance(NodeComponent::class.java)
    }
    
    // Cached grid for performance
    private var gridCache: Image? = null
    private var gridCacheZoom = 0.0
    private var gridCacheSize = Dimension(0, 0)

    init {
        // Initialize grid settings from canvas state
        _snapToGrid = canvasState.snapToGrid
        _showGrid = canvasState.showGrid
        _zoomFactor = canvasState.zoomFactor
        
        layout = null // Free positioning
        background = CanvasColors.CANVAS_BACKGROUND

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
        
        // Don't need a fixed size since we're not using scrollbars anymore
        // The canvas will be sized to fit the parent container
    }

    private fun initializeNodes() {
        canvasState.nodes.values.forEach { node ->
            nodeManager.addNodeComponent(node)
        }
    }

    fun saveState() {
        CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)
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
    
    /**
     * Zooms by a custom factor (for more precise control with trackpad gestures)
     */
    fun zoomBy(factor: Double) {
        zoomManager.zoomBy(factor)
    }
    
    fun zoomBy(factor: Double, centerPoint: Point) {
        zoomManager.zoomBy(factor, centerPoint)
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
        
        // Invalidate grid cache
        invalidateGridCache()
    }
    
    fun setShowGrid(value: Boolean) {
        _showGrid = value
        
        // Update canvas state
        canvasState.setGridPreferences(_snapToGrid, _showGrid)
        
        // Persist the state
        CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)
        
        // Invalidate grid cache
        invalidateGridCache()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g.create() as Graphics2D

        // Set rendering hints for smoother lines
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Draw grid if enabled using cached grid
        if (showGrid) {
            // Check if we need to refresh the grid cache
            if (needToRegenerateGridCache()) {
                generateGridCache()
            }
            // Draw cached grid
            gridCache?.let { g2d.drawImage(it, 0, 0, null) }
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
    
    /**
     * Check if we need to regenerate the grid cache
     */
    private fun needToRegenerateGridCache(): Boolean {
        // Only check if grid is being shown
        if (!showGrid) return false
        
        // If no cache exists, we need to generate it
        if (gridCache == null) return true
        
        // If zoom changed, we need to regenerate
        if (gridCacheZoom != zoomFactor) return true
        
        // If panel size increased beyond cache size, regenerate
        if (gridCacheSize.width < width || gridCacheSize.height < height) return true
        
        return false
    }
    
    /**
     * Generate the grid cache image for better performance
     */
    private fun generateGridCache() {
        val scaledGridSize = (GRID_SIZE * zoomFactor).toInt()
        
        // Create image with some padding for scrolling
        val cacheWidth = width + scaledGridSize 
        val cacheHeight = height + scaledGridSize
        
        // Create the cache image at the current size
        val cache = createImage(cacheWidth, cacheHeight)
        val g2d = cache.graphics as Graphics2D
        
        // Fill background with explicit grid background color
        g2d.color = CanvasColors.GRID_BACKGROUND
        g2d.fillRect(0, 0, cacheWidth, cacheHeight)
        
        // Draw the grid lines
        g2d.color = CanvasColors.GRID_COLOR
        
        // Draw vertical lines
        var x = 0
        while (x < cacheWidth) {
            g2d.drawLine(x, 0, x, cacheHeight)
            x += scaledGridSize
        }

        // Draw horizontal lines
        var y = 0
        while (y < cacheHeight) {
            g2d.drawLine(0, y, cacheWidth, y)
            y += scaledGridSize
        }
        
        g2d.dispose()
        
        // Store the cache and its properties
        gridCache = cache
        gridCacheZoom = zoomFactor
        gridCacheSize = Dimension(cacheWidth, cacheHeight)
    }
    
    /**
     * Force regenerate grid cache, e.g. when theme changes
     */
    fun invalidateGridCache() {
        gridCache = null
        repaint()
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
