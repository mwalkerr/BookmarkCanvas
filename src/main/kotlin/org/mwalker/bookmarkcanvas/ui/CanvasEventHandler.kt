package org.mwalker.bookmarkcanvas.ui

import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import java.awt.Cursor
import java.awt.Point
import java.awt.event.*
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/**
 * Handles events for the canvas panel
 */
class CanvasEventHandler(
    private val canvasPanel: CanvasPanel,
    private val project: Project
) {
    private val LOG = Logger.getInstance(CanvasEventHandler::class.java)
    
    // Throttlers for mouse movement operations
    private val panningThrottler = EventThrottler(16) // ~60fps
    private val nodeDragThrottler = EventThrottler(16) // ~60fps
    private val connectionThrottler = EventThrottler(16) // ~60fps
    private val mouseMoveThrottler = EventThrottler(50) // Lower frequency for mouse moves
    
    /**
     * Checks if a modifier key (Ctrl/Cmd) is pressed
     */
    private fun isModifierKeyDown(e: InputEvent): Boolean {
        return e.isControlDown || e.isMetaDown
    }
    
    /**
     * Sets up all event listeners for the canvas
     */
    fun setupEventListeners() {
        // Mouse listener for creating new connections and canvas interaction
        val mouseAdapter = createMouseAdapter()
        
        // Mouse wheel listener for zooming
        val mouseWheelListener = createMouseWheelListener()

        canvasPanel.addMouseListener(mouseAdapter)
        canvasPanel.addMouseMotionListener(mouseAdapter)
        canvasPanel.addMouseWheelListener(mouseWheelListener)
        
        // Key listener for keyboard shortcuts
        canvasPanel.addKeyListener(createKeyAdapter())
        
        // Make the panel focusable to receive key events
        canvasPanel.isFocusable = true
    }
    
    /**
     * Creates the mouse adapter for handling mouse events
     */
    private fun createMouseAdapter(): MouseAdapter {
        return object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                LOG.info("Mouse pressed on canvas, ${e.point}, ${e.button}, clickCount: ${e.clickCount} "+
                        "isPopupTrigger: ${e.isPopupTrigger}, isLeft: ${SwingUtilities.isLeftMouseButton(e)}, isRight: ${SwingUtilities.isRightMouseButton(e)}")

                canvasPanel.requestFocusInWindow() // Ensure panel can receive key events


                if (SwingUtilities.isLeftMouseButton(e)) {

                    if (isModifierKeyDown(e) && e.source == canvasPanel) {
                        // Ctrl/Cmd + left click on canvas starts panning
                        canvasPanel.isPanning = true
                        canvasPanel.dragStartPoint = e.point
                        canvasPanel.cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                    } else if (e.source == canvasPanel && canvasPanel.getComponentAt(e.point) == canvasPanel) {
                        // Left click on canvas starts selection box
                        canvasPanel.isDrawingSelectionBox = true
                        canvasPanel.selectionStart = e.point
                        canvasPanel.selectionEnd = e.point
                        
                        // Clear selection if not holding shift
                        if (!e.isShiftDown) {
                            canvasPanel.clearSelection()
                        }
                    } else {
                        // Clicking on a component that isn't the canvas directly
                        // Check if it's from a forwarded event from a node
                        val targetNode = if (e.source is NodeComponent) {
                            e.source as NodeComponent
                        } else {
                            canvasPanel.getComponentAt(e.point) as? NodeComponent
                        }
                        
                        if (targetNode != null) {
                            canvasPanel.dragStartPoint = e.point
                            
                            // Update selection based on shift key
                            if (!e.isShiftDown && !canvasPanel.selectedNodes.contains(targetNode)) {
                                canvasPanel.clearSelection()
                            }
                            
                            // Add to selection
                            if (!canvasPanel.selectedNodes.contains(targetNode)) {
                                canvasPanel.selectedNodes.add(targetNode)
                                targetNode.isSelected = true
                                targetNode.repaint()
                            }
                            
                            // This is important - prevent child components from handling this event
                            e.consume()
                        }
                    }
                } else if (e.isPopupTrigger) {
                    // Store point for potential context menu
                    canvasPanel.dragStartPoint = e.point
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                LOG.info("Mouse released on canvas, ${e.point}, ${e.button}, clickCount: ${e.clickCount} "+
                        "isPopupTrigger: ${e.isPopupTrigger}, isLeft: ${SwingUtilities.isLeftMouseButton(e)}, isRight: ${SwingUtilities.isRightMouseButton(e)}, " +
                        "connectionStartNode is null: ${canvasPanel.connectionStartNode == null}, isPanning: ${canvasPanel.isPanning}, isDrawingSelectionBox: ${canvasPanel.isDrawingSelectionBox}, " +
                        "component at point: ${canvasPanel.getComponentAt(e.point)}")

                // Clear any pending throttled actions
                panningThrottler.clear()
                nodeDragThrottler.clear()
                connectionThrottler.clear()

                // Flag to track if we need to save state
                var shouldSaveState = false

                if (canvasPanel.connectionStartNode != null) {
                    // Try to find if there's a node at the release point
                    val comp = canvasPanel.getComponentAt(e.point)
                    if (comp is NodeComponent && comp != canvasPanel.connectionStartNode) {
                        val targetNode = comp
                        canvasPanel.createNewConnection(canvasPanel.connectionStartNode!!.node, targetNode.node)
                        shouldSaveState = true
                    }
                    canvasPanel.connectionStartNode = null
                    canvasPanel.tempConnectionEndPoint = null
                    canvasPanel.repaint()
                } else if (canvasPanel.isPanning) {
                    // When panning ends, we should save the state
                    canvasPanel.isPanning = false
                    canvasPanel.cursor = Cursor.getDefaultCursor()
                    shouldSaveState = true
                } else if (canvasPanel.isDrawingSelectionBox) {
                    canvasPanel.isDrawingSelectionBox = false
                    canvasPanel.finalizeSelection()
                    canvasPanel.selectionStart = null
                    canvasPanel.selectionEnd = null
                    canvasPanel.repaint()
                } else if ((e.isPopupTrigger || SwingUtilities.isRightMouseButton(e)) &&
                         e.source == canvasPanel && 
                         canvasPanel.getComponentAt(e.point) == canvasPanel) {
                    LOG.info("isPopupTrigger on canvas, showing context menu")
                    // Show context menu only if right-click without drag on canvas background
                    if ((canvasPanel.dragStartPoint == null) || (
                                (Math.abs(e.point.x - canvasPanel.dragStartPoint!!.x) < 5 && Math.abs(e.point.y - canvasPanel.dragStartPoint!!.y) < 5))
                    ) {
                        LOG.info("Showing canvas context menu")
                        canvasPanel.showCanvasContextMenu(e.point)
                    }
                }
                
                // Save node positions if we were dragging nodes
                if (canvasPanel.dragStartPoint != null && !canvasPanel.isDrawingSelectionBox && canvasPanel.selectedNodes.isNotEmpty()) {
                    for (nodeComp in canvasPanel.selectedNodes) {
                        nodeComp.node.position = Point(
                            (nodeComp.x / canvasPanel.zoomFactor).toInt(),
                            (nodeComp.y / canvasPanel.zoomFactor).toInt()
                        )
                    }
                    shouldSaveState = true
                }
                
                // Save scroll position
                val scrollPane = canvasPanel.parent?.parent as? JScrollPane
                if (scrollPane != null && shouldSaveState) {
                    scrollPane.viewport?.let { viewport ->
                        val viewPosition = viewport.viewPosition
                        canvasPanel.canvasState.scrollPositionX = viewPosition.x
                        canvasPanel.canvasState.scrollPositionY = viewPosition.y
                    }
                }
                
                // Save state once at the end if any actions require it
                if (shouldSaveState) {
                    CanvasPersistenceService.getInstance().saveCanvasState(project, canvasPanel.canvasState)
                }
                
                canvasPanel.dragStartPoint = null
            }
            
            override fun mouseDragged(e: MouseEvent) {
                // We don't log mouse drag events for better performance
                if (canvasPanel.isPanning && canvasPanel.dragStartPoint != null) {
                    // Throttle panning events for better performance
                    panningThrottler.throttle {
                        handlePanning(e)
                    }
                } else if (canvasPanel.isDrawingSelectionBox) {
                    // Update selection box - no need to throttle this as it's just updating
                    // a variable and requesting repaint
                    canvasPanel.selectionEnd = e.point
                    canvasPanel.repaint()
                } else if (canvasPanel.dragStartPoint != null && canvasPanel.selectedNodes.isNotEmpty()) {
                    // Throttle node dragging events for better performance
                    nodeDragThrottler.throttle {
                        handleNodeDragging(e)
                    }
                } else if (canvasPanel.connectionStartNode != null) {
                    // Throttle connection drawing events
                    connectionThrottler.throttle {
                        canvasPanel.tempConnectionEndPoint = e.point
                        canvasPanel.repaint()
                    }
                }
            }
            
            override fun mouseMoved(e: MouseEvent) {
                // Throttle mouse move events to reduce CPU usage
                mouseMoveThrottler.throttle {
                    // Change cursor based on modifier key
                    if (isModifierKeyDown(e) && e.source == canvasPanel) {
                        canvasPanel.cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                    } else {
                        canvasPanel.cursor = Cursor.getDefaultCursor()
                    }
                }
            }
        }
    }
    
    /**
     * Handles panning behavior when dragging with Ctrl/Cmd pressed
     */
    private fun handlePanning(e: MouseEvent) {
        // Pan all components
        val dx = e.x - canvasPanel.dragStartPoint!!.x
        val dy = e.y - canvasPanel.dragStartPoint!!.y
        
        for (nodeComp in canvasPanel.nodeComponents.values) {
            val newX = nodeComp.x + dx
            val newY = nodeComp.y + dy
            nodeComp.setLocation(newX, newY)
            nodeComp.node.position = Point(
                (newX / canvasPanel.zoomFactor).toInt(), 
                (newY / canvasPanel.zoomFactor).toInt()
            )
        }
        
        // Update drag start point
        canvasPanel.dragStartPoint = e.point
        
        // Update scroll position in memory (but don't save to persistent storage during panning)
        val scrollPane = canvasPanel.parent?.parent as? JScrollPane
        scrollPane?.viewport?.let { viewport ->
            val viewPosition = viewport.viewPosition
            canvasPanel.canvasState.scrollPositionX = viewPosition.x
            canvasPanel.canvasState.scrollPositionY = viewPosition.y
            // No saving during panning - will save on mouse release instead
        }
        
        canvasPanel.repaint()
    }
    
    /**
     * Handles dragging of selected nodes
     */
    private fun handleNodeDragging(e: MouseEvent) {
        // Make sure drag was started on a node
        val comp = canvasPanel.getComponentAt(canvasPanel.dragStartPoint!!)
        
        if ((comp is NodeComponent && canvasPanel.selectedNodes.contains(comp)) || 
            (canvasPanel.selectedNodes.isNotEmpty() && e.source is NodeComponent && canvasPanel.selectedNodes.contains(e.source))) {
            // Calculate base movement
            val dx = e.x - canvasPanel.dragStartPoint!!.x
            val dy = e.y - canvasPanel.dragStartPoint!!.y
            
            // Only apply snap-to-grid when moving a single node
            if (canvasPanel.snapToGrid && canvasPanel.selectedNodes.size == 1) {
                val nodeComp = canvasPanel.selectedNodes.first()
                val gridSize = (canvasPanel.GRID_SIZE * canvasPanel.zoomFactor).toInt()
                val newX = (nodeComp.x + dx) / gridSize * gridSize
                val newY = (nodeComp.y + dy) / gridSize * gridSize
                nodeComp.setLocation(newX, newY)
            } else {
                // Move all selected nodes without snapping
                for (nodeComp in canvasPanel.selectedNodes) {
                    val newX = nodeComp.x + dx
                    val newY = nodeComp.y + dy
                    nodeComp.setLocation(newX, newY)
                }
            }
            
            // Update drag start point
            canvasPanel.dragStartPoint = e.point
            canvasPanel.repaint()
            
            // Consume the event to prevent child components from handling it
            e.consume()
        }
    }
    
    /**
     * Creates a mouse wheel listener for zoom behavior
     */
    private fun createMouseWheelListener(): MouseWheelListener {
        // Track the last zoom time to throttle rapid events
        var lastZoomTime = 0L
        val ZOOM_THROTTLE_MS = 50 // Minimum ms between zoom operations
        
        return MouseWheelListener { e ->
            val currentTime = System.currentTimeMillis()
            
            // Try to detect if this is a trackpad gesture
            val preciseRotation = try {
                // This is how macOS indicates a trackpad gesture
                e.javaClass.getMethod("getPreciseWheelRotation").invoke(e) as Double
            } catch (ex: Exception) {
                null
            }
            
            // Is this a trackpad gesture? Either:
            // 1. On macOS with precise rotation available and non-zero
            // 2. Or any platform with modifier key pressed (Ctrl/Cmd)
            val isPreciseWheelEvent = preciseRotation != null && Math.abs(preciseRotation) < 1.0
            val shouldZoom = isPreciseWheelEvent || isModifierKeyDown(e)
            
            if (shouldZoom && currentTime - lastZoomTime >= ZOOM_THROTTLE_MS) {
                // For trackpad pinch gestures, we want a smoother zoom experience
                if (isPreciseWheelEvent) {
                    LOG.info("Trackpad gesture detected with rotation: $preciseRotation")
                    
                    // Use a smaller zoom factor for precise control with trackpad
                    // Reverse the direction for natural feel with trackpad pinch
                    val zoomFactor = 1.0 + (preciseRotation!! * -0.05)
                    canvasPanel.zoomBy(zoomFactor, e.point)
                } else {
                    // Regular mouse wheel - use standard zoom steps
                    if (e.wheelRotation < 0) {
                        canvasPanel.zoomManager.zoomIn(e.point)  // Zoom in with cursor center
                    } else {
                        canvasPanel.zoomManager.zoomOut(e.point) // Zoom out with cursor center
                    }
                }
                
                lastZoomTime = currentTime
                
                // Consume the event to prevent scrolling
                e.consume()
            }
        }
    }
    
    /**
     * Creates a key adapter for keyboard shortcuts
     */
    private fun createKeyAdapter(): KeyAdapter {
        return object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ESCAPE -> {
                        // Clear selection
                        canvasPanel.clearSelection()
                        canvasPanel.repaint()
                    }
                    KeyEvent.VK_DELETE -> {
                        // Delete selected nodes
                        if (canvasPanel.selectedNodes.isNotEmpty()) {
                            for (nodeComp in canvasPanel.selectedNodes) {
                                canvasPanel.canvasState.removeNode(nodeComp.node.id)
                                canvasPanel.remove(nodeComp)
                                canvasPanel.nodeComponents.remove(nodeComp.node.id)
                            }
                            canvasPanel.selectedNodes.clear()
                            CanvasPersistenceService.getInstance().saveCanvasState(project, canvasPanel.canvasState)
                            canvasPanel.repaint()
                        }
                    }
                }
            }
        }
    }
}