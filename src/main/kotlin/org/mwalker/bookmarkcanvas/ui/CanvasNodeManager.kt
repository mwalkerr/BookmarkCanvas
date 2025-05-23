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

        // For new nodes, place at 0,0 or shift existing nodes if needed
        if (node.positionX == 100 && node.positionY == 100) {
            // Check if any node exists at 0,0
            val isOriginOccupied = canvasPanel.nodeComponents.values.any { 
                val posX = (it.node.positionX / canvasPanel.zoomFactor).toInt()
                val posY = (it.node.positionY / canvasPanel.zoomFactor).toInt()
                posX == 0 && posY == 0 
            }
            
            if (isOriginOccupied) {
                // Shift all existing nodes down to make space
                for (existingNode in canvasPanel.nodeComponents.values) {
                    existingNode.node.positionY += 100
                    val newY = (existingNode.node.positionY * canvasPanel.zoomFactor).toInt()
                    val newX = (existingNode.node.positionX * canvasPanel.zoomFactor).toInt()
                    existingNode.setLocation(newX, newY)
                }
            }
            
            // Place new node at 0,0
            node.positionX = 0
            node.positionY = 0
        }
        
        // Apply zoom and snap if necessary
        var x = (node.positionX * canvasPanel.zoomFactor).toInt()
        var y = (node.positionY * canvasPanel.zoomFactor).toInt()
        
        // We don't snap when initially adding nodes, only when dragging them
        // This ensures toggling snap doesn't change node positions

        val prefSize = nodeComponent.preferredSize
        val scaledWidth = (prefSize.width * canvasPanel.zoomFactor).toInt()
        val scaledHeight = (prefSize.height * canvasPanel.zoomFactor).toInt()

        nodeComponent.setBounds(x, y, scaledWidth, scaledHeight)
        
        // Apply font scaling to the new node
        nodeComponent.updateFontSizes(canvasPanel.zoomFactor)
        
        canvasPanel.nodeComponents[node.id] = nodeComponent
        
        // Clear existing selection and select the newly added node
        canvasPanel.selectionManager.clearSelection()
        canvasPanel.selectedNodes.add(nodeComponent)
        nodeComponent.isSelected = true
        nodeComponent.repaint()
        
        // Ensure canvas state is saved after adding node
        canvasPanel.saveState()
    }
}