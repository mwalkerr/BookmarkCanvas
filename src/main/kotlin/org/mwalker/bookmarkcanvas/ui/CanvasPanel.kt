package org.mwalker.bookmarkcanvas.ui

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.model.CanvasState
import org.mwalker.bookmarkcanvas.model.NodeConnection
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import java.awt.*
import java.awt.event.*
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import javax.swing.*

class CanvasPanel(val project: Project) : JPanel() {
    val canvasState: org.mwalker.bookmarkcanvas.model.CanvasState = CanvasPersistenceService.getInstance().getCanvasState(project)
    private val nodeComponents = mutableMapOf<String, org.mwalker.bookmarkcanvas.ui.NodeComponent>()
    val selectedNodes = mutableSetOf<org.mwalker.bookmarkcanvas.ui.NodeComponent>()
    var connectionStartNode: org.mwalker.bookmarkcanvas.ui.NodeComponent? = null
    private var dragStartPoint: Point? = null
    private var isPanning = false
    private var isDrawingSelectionBox = false
    private var selectionStart: Point? = null
    private var selectionEnd: Point? = null
    var tempConnectionEndPoint: Point? = null
    private var _zoomFactor = 1.0
    val zoomFactor: Double get() = _zoomFactor
    var snapToGrid = false
        private set
    var showGrid = false
        private set
    val GRID_SIZE = 20
    
    companion object {
        private val CANVAS_BACKGROUND = JBColor(
            Color(240, 240, 240), // Light mode
            Color(30, 30, 30) // Dark mode
        )
        private val GRID_COLOR = JBColor(
            Color(210, 210, 210), // Light mode
            Color(50, 50, 50) // Dark mode
        )
        private val SELECTION_BOX_COLOR = JBColor(
            Color(100, 150, 255, 50), // Light mode with transparency
            Color(80, 120, 200, 50) // Dark mode with transparency
        )
        private val SELECTION_BOX_BORDER_COLOR = JBColor(
            Color(70, 130, 230), // Light mode
            Color(100, 150, 230) // Dark mode
        )
    }

    init {
        layout = null // Free positioning
        background = CANVAS_BACKGROUND

        initializeNodes()
        setupEventListeners()

        // Initial size - large to allow unlimited panning
        preferredSize = Dimension(5000, 5000)
    }

    private fun initializeNodes() {
        canvasState.nodes.values.forEach { node ->
            addNodeComponent(node)
        }
    }

    fun addNodeComponent(node: BookmarkNode) {
        val nodeComponent = org.mwalker.bookmarkcanvas.ui.NodeComponent(node, project)
        add(nodeComponent)

        // Stagger new nodes to prevent overlap
        if (node.position.x == 100 && node.position.y == 100) {
            // Find a free position
            val offset = nodeComponents.size * 30
            node.position = Point(100 + offset, 100 + offset)
        }
        
        // Apply zoom and snap if necessary
        val pos = node.position
        var x = (pos.x * zoomFactor).toInt()
        var y = (pos.y * zoomFactor).toInt()
        
        // Apply snapping if enabled
        if (snapToGrid) {
            val gridSize = (GRID_SIZE * zoomFactor).toInt()
            x = (x / gridSize) * gridSize
            y = (y / gridSize) * gridSize
            node.position = Point(x / zoomFactor.toInt(), y / zoomFactor.toInt())
        }

        val prefSize = nodeComponent.preferredSize
        val scaledWidth = (prefSize.width * zoomFactor).toInt()
        val scaledHeight = (prefSize.height * zoomFactor).toInt()

        nodeComponent.setBounds(x, y, scaledWidth, scaledHeight)
        nodeComponents[node.id] = nodeComponent
    }

    private fun isModifierKeyDown(e: InputEvent): Boolean {
        return e.isControlDown || e.isMetaDown // Check for ctrl or cmd key
    }

    private fun setupEventListeners() {
        // Mouse listener for creating new connections and canvas interaction
        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow() // Ensure panel can receive key events
                
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (isModifierKeyDown(e) && e.source == this@CanvasPanel) {
                        // Ctrl/Cmd + left click on canvas starts panning
                        isPanning = true
                        dragStartPoint = e.point
                        cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                    } else if (e.source == this@CanvasPanel && getComponentAt(e.point) == this@CanvasPanel) {
                        // Left click on canvas starts selection box
                        isDrawingSelectionBox = true
                        selectionStart = e.point
                        selectionEnd = e.point
                        
                        // Clear selection if not holding shift
                        if (!e.isShiftDown) {
                            clearSelection()
                        }
                    } else {
                        // Clicking on a component that isn't the canvas
                        val comp = getComponentAt(e.point)
                        if (comp is org.mwalker.bookmarkcanvas.ui.NodeComponent) {
                            dragStartPoint = e.point
                            
                            // Update selection based on shift key
                            if (!e.isShiftDown && !selectedNodes.contains(comp)) {
                                clearSelection()
                            }
                            
                            // Add to selection
                            if (!selectedNodes.contains(comp)) {
                                selectedNodes.add(comp)
                                comp.isSelected = true
                                comp.repaint()
                            }
                            
                            // This is important - prevent child components from handling this event
                            e.consume()
                        }
                    }
                } else if (e.isPopupTrigger) {
                    // Store point for potential context menu
                    dragStartPoint = e.point
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (connectionStartNode != null) {
                    // Try to find if there's a node at the release point
                    val comp = getComponentAt(e.point)
                    if (comp is org.mwalker.bookmarkcanvas.ui.NodeComponent && comp != connectionStartNode) {
                        val targetNode = comp
                        createNewConnection(connectionStartNode!!.node, targetNode.node)
                    }
                    connectionStartNode = null
                    tempConnectionEndPoint = null
                    repaint()
                } else if (isPanning) {
                    isPanning = false
                    cursor = Cursor.getDefaultCursor()
                } else if (isDrawingSelectionBox) {
                    isDrawingSelectionBox = false
                    finalizeSelection()
                    selectionStart = null
                    selectionEnd = null
                    repaint()
                } else if (e.isPopupTrigger && 
                         e.source == this@CanvasPanel && 
                         getComponentAt(e.point) == this@CanvasPanel) {
                    // Show context menu only if right-click without drag on canvas background
                    if (dragStartPoint != null && 
                       (Math.abs(e.point.x - dragStartPoint!!.x) < 5 && Math.abs(e.point.y - dragStartPoint!!.y) < 5)) {
                        showCanvasContextMenu(e.point)
                    }
                }
                
                // Save node positions if we were dragging nodes
                if (dragStartPoint != null && !isPanning && !isDrawingSelectionBox && selectedNodes.isNotEmpty()) {
                    for (nodeComp in selectedNodes) {
                        nodeComp.node.position = Point(
                            (nodeComp.x / zoomFactor).toInt(),
                            (nodeComp.y / zoomFactor).toInt()
                        )
                    }
                    CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)
                }
                
                dragStartPoint = null
            }
            
            override fun mouseDragged(e: MouseEvent) {
                if (isPanning && dragStartPoint != null) {
                    // Pan all components
                    val dx = e.x - dragStartPoint!!.x
                    val dy = e.y - dragStartPoint!!.y
                    
                    for (nodeComp in nodeComponents.values) {
                        val newX = nodeComp.x + dx
                        val newY = nodeComp.y + dy
                        nodeComp.setLocation(newX, newY)
                        nodeComp.node.position = Point(
                            (newX / zoomFactor).toInt(), 
                            (newY / zoomFactor).toInt()
                        )
                    }
                    
                    // Update drag start point
                    dragStartPoint = e.point
                    repaint()
                } else if (isDrawingSelectionBox) {
                    // Update selection box
                    selectionEnd = e.point
                    repaint()
                } else if (dragStartPoint != null && selectedNodes.isNotEmpty()) {
                    // Make sure drag was started on a node
                    val comp = getComponentAt(dragStartPoint!!)
                    
                    if (comp is NodeComponent && selectedNodes.contains(comp)) {
                        // Drag all selected nodes
                        val dx = e.x - dragStartPoint!!.x
                        val dy = e.y - dragStartPoint!!.y
                        
                        for (nodeComp in selectedNodes) {
                            var newX = nodeComp.x + dx
                            var newY = nodeComp.y + dy
                            
                            // Apply snap-to-grid if enabled
                            if (snapToGrid) {
                                val gridSize = (GRID_SIZE * zoomFactor).toInt()
                                newX = (newX / gridSize) * gridSize
                                newY = (newY / gridSize) * gridSize
                            }
                            
                            nodeComp.setLocation(newX, newY)
                        }
                        
                        // Update drag start point
                        dragStartPoint = e.point
                        repaint()
                        
                        // Consume the event to prevent child components from handling it
                        e.consume()
                    }
                } else if (connectionStartNode != null) {
                    tempConnectionEndPoint = e.point
                    repaint()
                }
            }
            
            override fun mouseMoved(e: MouseEvent) {
                // Change cursor based on modifier key
                if (isModifierKeyDown(e) && e.source == this@CanvasPanel) {
                    cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                } else {
                    cursor = Cursor.getDefaultCursor()
                }
            }
        }
        
        // Mouse wheel listener for zooming
        val mouseWheelListener = MouseWheelListener { e ->
            if (isModifierKeyDown(e)) {
                // Pinch zoom - increment/decrement based on wheel direction
                if (e.wheelRotation < 0) {
                    // Zoom in
                    zoomIn()
                } else {
                    // Zoom out
                    zoomOut()
                }
            }
        }

        addMouseListener(mouseAdapter)
        addMouseMotionListener(mouseAdapter)
        addMouseWheelListener(mouseWheelListener)
        
        // Key listener for keyboard shortcuts
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ESCAPE -> {
                        // Clear selection
                        clearSelection()
                        repaint()
                    }
                    KeyEvent.VK_DELETE -> {
                        // Delete selected nodes
                        if (selectedNodes.isNotEmpty()) {
                            for (nodeComp in selectedNodes) {
                                canvasState.removeNode(nodeComp.node.id)
                                remove(nodeComp)
                                nodeComponents.remove(nodeComp.node.id)
                            }
                            selectedNodes.clear()
                            CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)
                            repaint()
                        }
                    }
                }
            }
        })
        
        // Make the panel focusable to receive key events
        isFocusable = true
    }
    
    private fun clearSelection() {
        for (nodeComp in selectedNodes) {
            nodeComp.isSelected = false
            nodeComp.repaint()
        }
        selectedNodes.clear()
    }
    
    private fun finalizeSelection() {
        if (selectionStart == null || selectionEnd == null) return
        
        val rect = getSelectionRectangle()
        
        // Select all nodes that intersect with the selection rectangle
        for (nodeComp in nodeComponents.values) {
            val nodeBounds = Rectangle(nodeComp.x, nodeComp.y, nodeComp.width, nodeComp.height)
            
            if (rect.intersects(nodeBounds)) {
                selectedNodes.add(nodeComp)
                nodeComp.isSelected = true
                nodeComp.repaint()
            }
        }
    }
    
    private fun getSelectionRectangle(): Rectangle {
        val x = Math.min(selectionStart!!.x, selectionEnd!!.x)
        val y = Math.min(selectionStart!!.y, selectionEnd!!.y)
        val width = Math.abs(selectionEnd!!.x - selectionStart!!.x)
        val height = Math.abs(selectionEnd!!.y - selectionStart!!.y)
        
        return Rectangle(x, y, width, height)
    }

    private fun createNewConnection(source: BookmarkNode, target: BookmarkNode) {
        val connection = NodeConnection(source.id, target.id)
        canvasState.addConnection(connection)
        CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)
        repaint()
    }

    private fun showCanvasContextMenu(point: Point) {
        val menu = JPopupMenu()
        val addNodeItem = JMenuItem("Add All Bookmarks")
        addNodeItem.addActionListener {
            refreshBookmarks()
        }
        menu.add(addNodeItem)

        val clearCanvasItem = JMenuItem("Clear Canvas")
        clearCanvasItem.addActionListener {
            clearCanvas()
        }
        menu.add(clearCanvasItem)

        menu.show(this, point.x, point.y)
    }

    private fun refreshBookmarks() {
        // This would fetch all bookmarks from the IDE
        // For each bookmark not already on canvas, add a new node
        // Code to integrate with IDE bookmark system would go here
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
        _zoomFactor *= 1.2
        updateCanvasSize()
        repaint()
    }

    fun zoomOut() {
        _zoomFactor /= 1.2
        if (_zoomFactor < 0.1) _zoomFactor = 0.1
        updateCanvasSize()
        repaint()
    }

    fun setSnapToGrid(value: Boolean) {
        snapToGrid = value
        showGrid = value // Show grid when snap is enabled
        if (snapToGrid) {
            // Snap all existing nodes to grid
            for (nodeComp in nodeComponents.values) {
                val location = nodeComp.location
                val x = (location.x / GRID_SIZE).toInt() * GRID_SIZE
                val y = (location.y / GRID_SIZE).toInt() * GRID_SIZE
                nodeComp.setLocation(x, y)
                nodeComp.node.position = Point((x / zoomFactor).toInt(), (y / zoomFactor).toInt())
            }
        }
        repaint()
    }
    
    fun setShowGrid(value: Boolean) {
        showGrid = value
        repaint()
    }

    private fun updateCanvasSize() {
        // Keep canvas size large regardless of zoom to allow unlimited panning
        preferredSize = Dimension(5000, 5000)

        // Update the scale for all components
        for (nodeComp in nodeComponents.values) {
            val originalPos = nodeComp.node.position
            val scaledX = (originalPos.x * zoomFactor).toInt()
            val scaledY = (originalPos.y * zoomFactor).toInt()
            nodeComp.setLocation(scaledX, scaledY)

            // Scale the size too
            val prefSize = nodeComp.preferredSize
            val scaledWidth = (prefSize.width * zoomFactor).toInt()
            val scaledHeight = (prefSize.height * zoomFactor).toInt()
            nodeComp.setSize(scaledWidth, scaledHeight)
        }

        revalidate()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g.create() as Graphics2D

        // Set rendering hints for smoother lines
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Draw grid if enabled
        if (showGrid) {
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

        // Draw connections between nodes
        for (connection in canvasState.connections) {
            val source = nodeComponents[connection.sourceNodeId]
            val target = nodeComponents[connection.targetNodeId]

            if (source != null && target != null) {
                drawConnection(g2d, source, target, connection.color)
            }
        }

        // Draw temporary connection if creating one
        connectionStartNode?.let { startNode ->
            tempConnectionEndPoint?.let { endPoint ->
                val startPoint = Point(
                    startNode.x + startNode.width / 2,
                    startNode.y + startNode.height / 2
                )
                g2d.color = JBColor.GRAY
                g2d.stroke = BasicStroke(
                    2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    0f, floatArrayOf(5f), 0f
                )
                g2d.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y)
            }
        }

        g2d.dispose()
    }
    
    // Paint the selection box in the glass pane layer to ensure it's on top
    override fun paint(g: Graphics) {
        super.paint(g)
        
        // After painting everything else, draw the selection box on top if active
        if (isDrawingSelectionBox && selectionStart != null && selectionEnd != null) {
            val g2d = g.create() as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            
            val rect = getSelectionRectangle()
            
            // Fill with semi-transparent color
            g2d.color = SELECTION_BOX_COLOR
            g2d.fill(rect)
            
            // Draw border
            g2d.color = SELECTION_BOX_BORDER_COLOR
            g2d.stroke = BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2d.draw(rect)
            
            g2d.dispose()
        }
    }

    private fun drawConnection(g2d: Graphics2D, source: org.mwalker.bookmarkcanvas.ui.NodeComponent, target: org.mwalker.bookmarkcanvas.ui.NodeComponent, color: Color) {
        // Calculate center points
        val startPoint = Point(
            source.x + source.width / 2,
            source.y + source.height / 2
        )

        val endPoint = Point(
            target.x + target.width / 2,
            target.y + target.height / 2
        )

        // Draw the line
        g2d.color = color
        g2d.stroke = BasicStroke(2.0f)
        g2d.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y)

        // Draw the arrowhead
        drawArrowHead(g2d, startPoint, endPoint)
    }

    private fun drawArrowHead(g2d: Graphics2D, start: Point, end: Point) {
        // Calculate arrowhead
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = Math.sqrt((dx * dx + dy * dy).toDouble())
        val dirX = dx / length
        val dirY = dy / length

        val arrowSize = 10.0
        val arrowAngle = Math.PI / 6 // 30 degrees

        val p1 = Point2D.Double(
            end.x - arrowSize * (dirX * Math.cos(arrowAngle) + dirY * Math.sin(arrowAngle)),
            end.y - arrowSize * (dirY * Math.cos(arrowAngle) - dirX * Math.sin(arrowAngle))
        )

        val p2 = Point2D.Double(
            end.x - arrowSize * (dirX * Math.cos(arrowAngle) - dirY * Math.sin(arrowAngle)),
            end.y - arrowSize * (dirY * Math.cos(arrowAngle) + dirX * Math.sin(arrowAngle))
        )

        // Draw arrowhead
        g2d.draw(Line2D.Double(end.x.toDouble(), end.y.toDouble(), p1.x, p1.y))
        g2d.draw(Line2D.Double(end.x.toDouble(), end.y.toDouble(), p2.x, p2.y))
    }
}