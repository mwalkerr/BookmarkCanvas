package org.mwalker.bookmarkcanvas.ui

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.event.*
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import kotlin.math.abs

/**
 * Handles all mouse and keyboard events for a NodeComponent
 */
class NodeEventHandler(
    private val nodeComponent: NodeComponent,
    private val node: BookmarkNode,
    private val project: Project,
    private val contextMenu: JPopupMenu,
    private val handleSize: Int
) {
    private val LOG = Logger.getInstance(NodeEventHandler::class.java)
    
    // Throttlers for mouse movement operations
    private val nodeDragThrottler = EventThrottler(16) // ~60fps
    private val nodeResizeThrottler = EventThrottler(16) // ~60fps
    private val connectionDragThrottler = EventThrottler(16) // ~60fps
    
    // State variables
    private var dragStart: Point? = null
    private var isDragging = false
    private var isResizing = false
    private var twoFingerTapStartPoint: Point? = null
    private var connectionStarted = false
    
    /**
     * Sets up mouse listener and motion listener for drag/resize behavior
     */
    fun setupDragBehavior() {
        val adapter = createMouseAdapter()
        nodeComponent.addMouseListener(adapter)
        nodeComponent.addMouseMotionListener(adapter)
    }
    
    /**
     * Sets up keyboard navigation handlers
     */
    fun setupKeyboardNavigation() {
        // Add keyboard navigation
        nodeComponent.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        // Navigate to bookmark
                        node.navigateToBookmark(project)
                    }
                    KeyEvent.VK_DELETE -> {
                        // Remove from canvas
                        val canvas = nodeComponent.parent as? CanvasPanel
                        canvas?.let {
                            it.canvasState.removeNode(node.id)
                            it.remove(nodeComponent)
                            it.repaint()
                            CanvasPersistenceService.getInstance().saveCanvasState(project, it.canvasState)
                        }
                    }
                }
            }
        })

        // Make component focusable
        nodeComponent.isFocusable = true
    }
    
    /**
     * Creates a mouse adapter that handles all mouse interactions
     */
    private fun createMouseAdapter(): MouseAdapter {
        return object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                LOG.info("Mouse pressed on node: ${node.getDisplayText()}, ${e.point}, " +
                        "button: ${e.button}, clickCount: ${e.clickCount}, " +
                        "isPopupTrigger: ${e.isPopupTrigger}, " +
                        "isLeft: ${SwingUtilities.isLeftMouseButton(e)}, " + 
                        "isRight: ${SwingUtilities.isRightMouseButton(e)}")
                
                // This will get the parent CanvasPanel
                val canvas = nodeComponent.parent as? CanvasPanel ?: return
                
                if (SwingUtilities.isLeftMouseButton(e)) {
                    val resizeArea = isInResizeArea(e.point, nodeComponent.width, nodeComponent.height, handleSize)
                    if (resizeArea) {
                        isResizing = true
                        dragStart = e.point
                        e.consume() // Consume the event so it doesn't propagate
                    } else if (canvas.selectedNodes.contains(nodeComponent) && canvas.selectedNodes.size > 1) {
                        // If we're part of a multi-selection group, forward the event to the canvas
                        forwardMouseEvent(nodeComponent, e, canvas)
                    } else {
                        // Individual dragging
                        isDragging = true
                        dragStart = e.point
                    }
                } else if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger) {
                    // Check if this node is part of a multi-selection
                    if (canvas.selectedNodes.contains(nodeComponent) && canvas.selectedNodes.size > 1) {
                        // Forward to canvas for group context menu
                        forwardMouseEvent(nodeComponent, e, canvas)
                        e.consume()
                    } else {
                        // Two-finger tap handling for single node
                        twoFingerTapStartPoint = e.point
                        connectionStarted = false
                    }
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                LOG.info("Mouse released on node: ${node.getDisplayText()}, ${e.point}, " +
                        "button: ${e.button}, clickCount: ${e.clickCount}, " +
                        "isPopupTrigger: ${e.isPopupTrigger}, " +
                        "isLeft: ${SwingUtilities.isLeftMouseButton(e)}, " + 
                        "isRight: ${SwingUtilities.isRightMouseButton(e)}")
                
                // Clear any pending throttled actions
                nodeDragThrottler.clear()
                nodeResizeThrottler.clear()
                connectionDragThrottler.clear()
                
                val canvas = nodeComponent.parent as? CanvasPanel ?: return
                
                if (isDragging || isResizing) {
                    // Save position when released
                    val location = nodeComponent.location
                    node.position = Point(
                        (location.x / (canvas.zoomFactor)).toInt(),
                        (location.y / (canvas.zoomFactor)).toInt()
                    )
                    
                    // Save size when released (if resizing occurred)
                    if (isResizing) {
                        // Normalize size by zoom factor, just like we do with position
                        node.width = (nodeComponent.width / canvas.zoomFactor).toInt()
                        node.height = (nodeComponent.height / canvas.zoomFactor).toInt()
                    }
                    
                    // Save changes to canvas state only now (after mouseReleased)
                    CanvasPersistenceService.getInstance().saveCanvasState(project, canvas.canvasState)
                    
                    isDragging = false
                    isResizing = false
                } else if (canvas.selectedNodes.contains(nodeComponent) && canvas.selectedNodes.size > 1) {
                    // If we're part of a multi-selection group, forward the event to the canvas
                    forwardMouseEvent(nodeComponent, e, canvas)
                } else if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger) {
                    // Check if this is a connection creation
                    if (connectionStarted) {
                        // Forward connection completion event to canvas
                        forwardMouseEvent(nodeComponent, e, canvas)
                    } else if (canvas.selectedNodes.contains(nodeComponent) && canvas.selectedNodes.size > 1) {
                        // Forward to canvas for group context menu
                        forwardMouseEvent(nodeComponent, e, canvas)
                    } else {
                        // Only show context menu on release if we haven't started a connection
                        LOG.info("Showing context menu")
                        contextMenu.show(nodeComponent, e.x, e.y)
                    }
                    twoFingerTapStartPoint = null
                    connectionStarted = false
                }
                
                dragStart = null
            }

            override fun mouseDragged(e: MouseEvent) {
                // No logging for drag events for better performance
                val canvas = nodeComponent.parent as? CanvasPanel ?: return
                
                // Check if we're part of a multi-selection
                if (canvas.selectedNodes.contains(nodeComponent) && canvas.selectedNodes.size > 1) {
                    // Forward the event to the canvas for group dragging
                    forwardMouseEvent(nodeComponent, e, canvas)
                } else if (isDragging) {
                    // Throttle individual node dragging 
                    nodeDragThrottler.throttle {
                        handleDragging(e)
                    }
                } else if (isResizing) {
                    // Throttle resizing operations
                    nodeResizeThrottler.throttle {
                        handleResizing(e)
                    }
                } else if (twoFingerTapStartPoint != null && 
                        (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger)) {
                    // Throttle connection creation operations
                    connectionDragThrottler.throttle {
                        handleConnectionDrag(e)
                    }
                }
            }
            
            override fun mouseClicked(e: MouseEvent) {
                LOG.info("Mouse clicked on node: ${node.getDisplayText()}, ${e.point}, " +
                        "button: ${e.button}, clickCount: ${e.clickCount}, " +
                        "isPopupTrigger: ${e.isPopupTrigger}, " +
                        "isLeft: ${SwingUtilities.isLeftMouseButton(e)}, " + 
                        "isRight: ${SwingUtilities.isRightMouseButton(e)}")

                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2) {
                    // Navigate to bookmark on double-click anywhere in the node
                    node.navigateToBookmark(project)
                }
            }
            
            override fun mouseMoved(e: MouseEvent) {
                // Change cursor when over resize area
                if (isInResizeArea(e.point, nodeComponent.width, nodeComponent.height, handleSize)) {
                    nodeComponent.cursor = Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
                } else {
                    nodeComponent.cursor = Cursor.getDefaultCursor()
                }
            }
        }
    }
    
    /**
     * Handles node dragging logic
     */
    private fun handleDragging(e: MouseEvent) {
        val canvas = nodeComponent.parent as? CanvasPanel ?: return
        val current = e.point
        val location = nodeComponent.location

        // Calculate new position
        var newX = location.x + (current.x - dragStart!!.x)
        var newY = location.y + (current.y - dragStart!!.y)

        // Apply snap-to-grid if enabled
        if (canvas.snapToGrid) {
            val gridSize = (canvas.GRID_SIZE * canvas.zoomFactor).toInt()
            newX = (newX / gridSize) * gridSize
            newY = (newY / gridSize) * gridSize
        }

        // Set new position
        nodeComponent.setLocation(newX, newY)

        // Repaint parent to update connections
        canvas.repaint()
    }
    
    /**
     * Handles node resizing logic
     */
    private fun handleResizing(e: MouseEvent) {
        val canvas = nodeComponent.parent as? CanvasPanel
        val current = e.point
        val widthDelta = current.x - dragStart!!.x
        val heightDelta = current.y - dragStart!!.y
        
        // Calculate new size
        val newWidth = (nodeComponent.width + widthDelta).coerceAtLeast(
            (120 * (canvas?.zoomFactor ?: 1.0)).toInt()
        )
        val newHeight = (nodeComponent.height + heightDelta).coerceAtLeast(
            (40 * (canvas?.zoomFactor ?: 1.0)).toInt()
        )
        
        // Update size - use both for better compatibility
        val newSize = Dimension(newWidth, newHeight)
        nodeComponent.setSize(newSize)
        nodeComponent.preferredSize = newSize
        
        // Signal to NodeComponent that the size has changed (for title text wrapping)
        (nodeComponent as? NodeComponentInternal)?.handleResize(newWidth)
        
        // Update drag start point
        dragStart = current
        
        nodeComponent.revalidate()
        nodeComponent.repaint()
        nodeComponent.parent?.repaint()  // Update connections
    }
    
    /**
     * Interface for NodeComponent operations needed by the event handler
     */
    interface NodeComponentInternal {
        fun handleResize(newWidth: Int)
    }
    
    /**
     * Handles connection creation drag
     */
    private fun handleConnectionDrag(e: MouseEvent) {
        val tapPoint = twoFingerTapStartPoint ?: return
        
        if (abs(e.point.x - tapPoint.x) > 5 || abs(e.point.y - tapPoint.y) > 5) {
            // We've moved enough to consider this a connection drag
            connectionStarted = true
            
            val canvas = nodeComponent.parent as? CanvasPanel ?: return
            
            if (canvas.connectionStartNode != nodeComponent) {
                canvas.connectionStartNode = nodeComponent
            }
            
            val canvasPoint = SwingUtilities.convertPoint(
                nodeComponent, e.point, canvas
            )
            canvas.tempConnectionEndPoint = canvasPoint
            canvas.repaint()
        }
    }
}