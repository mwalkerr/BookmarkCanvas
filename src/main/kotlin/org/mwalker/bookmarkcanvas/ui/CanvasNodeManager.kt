package org.mwalker.bookmarkcanvas.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.mwalker.bookmarkcanvas.model.BookmarkNode
import java.awt.Point

/**
 * Manages node components on the canvas
 */
class CanvasNodeManager(
    private val canvasPanel: CanvasPanel,
    private val project: Project
) {
    private val LOG = Logger.getInstance(CanvasNodeManager::class.java)
    
    /**
     * Adds a node component to the canvas
     */
    fun addNodeComponent(node: BookmarkNode) {
        val nodeComponent = NodeComponent(node, project)
        canvasPanel.add(nodeComponent)

        // Stagger new nodes to prevent overlap
        if (node.positionX == 100 && node.positionY == 100) {
            // Find a free position
            val offset = canvasPanel.nodeComponents.size * 30
            node.positionX = 100 + offset
            node.positionY = 100 + offset
        }
        
        // Apply zoom and snap if necessary
        var x = (node.positionX * canvasPanel.zoomFactor).toInt()
        var y = (node.positionY * canvasPanel.zoomFactor).toInt()
        
        // Apply snapping if enabled
        if (canvasPanel.snapToGrid) {
            val gridSize = (canvasPanel.GRID_SIZE * canvasPanel.zoomFactor).toInt()
            x = (x / gridSize) * gridSize
            y = (y / gridSize) * gridSize
            node.positionX = (x / canvasPanel.zoomFactor).toInt()
            node.positionY = (y / canvasPanel.zoomFactor).toInt()
        }

        val prefSize = nodeComponent.preferredSize
        val scaledWidth = (prefSize.width * canvasPanel.zoomFactor).toInt()
        val scaledHeight = (prefSize.height * canvasPanel.zoomFactor).toInt()

        nodeComponent.setBounds(x, y, scaledWidth, scaledHeight)
        
        // Apply font scaling to the new node
        nodeComponent.updateFontSizes(canvasPanel.zoomFactor)
        
        canvasPanel.nodeComponents[node.id] = nodeComponent
    }
}