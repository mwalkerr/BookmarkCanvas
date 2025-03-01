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
import javax.swing.*

class CanvasPanel(val project: Project) : JPanel() {
    val canvasState: org.mwalker.bookmarkcanvas.model.CanvasState = CanvasPersistenceService.getInstance().getCanvasState(project)
    private val nodeComponents = mutableMapOf<String, org.mwalker.bookmarkcanvas.ui.NodeComponent>()
    private var selectedNode: org.mwalker.bookmarkcanvas.ui.NodeComponent? = null
    var connectionStartNode: org.mwalker.bookmarkcanvas.ui.NodeComponent? = null
    private var dragStartPoint: Point? = null
    private var tempConnectionEndPoint: Point? = null
    private var zoomFactor = 1.0
    private var snapToGrid = false
    private var showGrid = false
    private val GRID_SIZE = 20
    
    companion object {
        private val CANVAS_BACKGROUND = JBColor(
            Color(240, 240, 240), // Light mode
            Color(30, 30, 30) // Dark mode
        )
        private val GRID_COLOR = JBColor(
            Color(210, 210, 210), // Light mode
            Color(50, 50, 50) // Dark mode
        )
    }

    init {
        layout = null // Free positioning
        background = CANVAS_BACKGROUND

        initializeNodes()
        setupEventListeners()

        // Initial size
        preferredSize = Dimension(2000, 2000)
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

    private fun setupEventListeners() {
        // Mouse listener for creating new connections and canvas interaction
        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger) {
                    // Show context menu for adding nodes or editing canvas
                    showCanvasContextMenu(e.point)
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
                }
            }
        }

        addMouseListener(mouseAdapter)

        // For drawing temporary connection line while dragging
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if (connectionStartNode != null) {
                    tempConnectionEndPoint = e.point
                    repaint()
                }
            }
        })
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
        canvasState.nodes.clear()
        canvasState.connections.clear()
        CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)
        repaint()
    }

    fun zoomIn() {
        zoomFactor *= 1.2
        updateCanvasSize()
        repaint()
    }

    fun zoomOut() {
        zoomFactor /= 1.2
        if (zoomFactor < 0.1) zoomFactor = 0.1
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
                nodeComp.node.position = Point(x, y)
            }
        }
        repaint()
    }
    
    fun setShowGrid(value: Boolean) {
        showGrid = value
        repaint()
    }

    private fun updateCanvasSize() {
        // Update the preferred size based on zoom level
        preferredSize = Dimension(
            (2000 * zoomFactor).toInt(),
            (2000 * zoomFactor).toInt()
        )

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