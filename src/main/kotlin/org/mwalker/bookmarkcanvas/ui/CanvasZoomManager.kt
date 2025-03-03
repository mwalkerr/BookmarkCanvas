package org.mwalker.bookmarkcanvas.ui

import com.intellij.openapi.diagnostic.Logger
import java.awt.Dimension
import java.awt.Point

/**
 * Manages zoom and navigation on the canvas
 */
class CanvasZoomManager(
    private val canvasPanel: CanvasPanel,
    private val nodeComponents: MutableMap<String, NodeComponent>
) {
    private val LOG = Logger.getInstance(CanvasZoomManager::class.java)
    
    /**
     * Increases the zoom level
     */
    fun zoomIn() {
        canvasPanel._zoomFactor *= 1.2
        canvasPanel.canvasState.zoomFactor = canvasPanel._zoomFactor
        updateCanvasSize()
        
        // Explicitly repaint all components to ensure text visibility
        for (nodeComp in nodeComponents.values) {
            nodeComp.revalidate()
            nodeComp.repaint()
        }
        
        // Force grid cache update since zoom level changed
        canvasPanel.invalidateGridCache()
        
        saveViewState()
        canvasPanel.repaint()
    }

    /**
     * Decreases the zoom level
     */
    fun zoomOut() {
        canvasPanel._zoomFactor /= 1.2
        if (canvasPanel._zoomFactor < 0.1) canvasPanel._zoomFactor = 0.1
        canvasPanel.canvasState.zoomFactor = canvasPanel._zoomFactor
        updateCanvasSize()
        
        // Explicitly repaint all components to ensure text visibility
        for (nodeComp in nodeComponents.values) {
            nodeComp.revalidate()
            nodeComp.repaint()
        }
        
        // Force grid cache update since zoom level changed
        canvasPanel.invalidateGridCache()
        
        saveViewState()
        canvasPanel.repaint()
    }
    
    /**
     * Saves the current view state (zoom) to the canvas state
     */
    private fun saveViewState() {
        // Since we no longer use scrollbars, we just need to save the zoom factor
        
        // Save state using persistence service
        org.mwalker.bookmarkcanvas.services.CanvasPersistenceService.getInstance()
            .saveCanvasState(canvasPanel.project, canvasPanel.canvasState)
    }
    
    /**
     * Updates the canvas size and scales all components
     */
    fun updateCanvasSize() {
        // Adjust size to fit parent container
        canvasPanel.parent?.let { parent ->
            canvasPanel.preferredSize = parent.size
        }

        // Update the scale for all components
        for (nodeComp in nodeComponents.values) {
            val originalPos = nodeComp.node.position
            val scaledX = (originalPos.x * canvasPanel.zoomFactor).toInt()
            val scaledY = (originalPos.y * canvasPanel.zoomFactor).toInt()
            nodeComp.setLocation(scaledX, scaledY)

            // Scale the size based on the node's persisted size or preferred size
            if (nodeComp.node.width > 0 && nodeComp.node.height > 0) {
                // Use persisted size scaled to current zoom
                val scaledWidth = (nodeComp.node.width * canvasPanel.zoomFactor).toInt()
                val scaledHeight = (nodeComp.node.height * canvasPanel.zoomFactor).toInt()
                nodeComp.setSize(scaledWidth, scaledHeight)
                nodeComp.preferredSize = Dimension(scaledWidth, scaledHeight)
            } else {
                // Fall back to preferred size
                val prefSize = nodeComp.preferredSize
                val scaledWidth = (prefSize.width * canvasPanel.zoomFactor).toInt()
                val scaledHeight = (prefSize.height * canvasPanel.zoomFactor).toInt()
                nodeComp.setSize(scaledWidth, scaledHeight)
            }
            
            // Update font sizes based on zoom factor
            nodeComp.updateFontSizes(canvasPanel.zoomFactor)
        }

        canvasPanel.revalidate()
    }
    
    /**
     * Positions the canvas to show the top-left node
     */
    fun goToTopLeftNode() {
        // Find the top-left most node (minimum x and y position)
        if (canvasPanel.canvasState.nodes.isEmpty()) return

        val minX = canvasPanel.canvasState.nodes.values.minOfOrNull {
            it.positionX
        } ?: return

        val minY = canvasPanel.canvasState.nodes.values.minOfOrNull {
            it.positionY
        } ?: return

        for (nodeComp in nodeComponents.values) {
            val x = nodeComp.node.positionX - minX
            val y = nodeComp.node.positionY - minY
            nodeComp.setLocation(x, y)
            nodeComp.node.positionX = x
            nodeComp.node.positionY = y
        }

        // Then reset the zoom factor
        canvasPanel._zoomFactor = 0.8
        canvasPanel.canvasState.zoomFactor = canvasPanel._zoomFactor
        updateCanvasSize()
        canvasPanel.invalidateGridCache() // Force grid cache update
        canvasPanel.repaint()
        
        // Save the state
        org.mwalker.bookmarkcanvas.services.CanvasPersistenceService.getInstance()
            .saveCanvasState(canvasPanel.project, canvasPanel.canvasState)
            
        LOG.info("Reset view position and saved state")
    }
}