package org.mwalker.bookmarkcanvas.ui

import java.awt.BasicStroke
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import org.mwalker.bookmarkcanvas.ui.CanvasColors

/**
 * Manages selection behavior on the canvas
 */
class CanvasSelectionManager(
    private val canvasPanel: CanvasPanel
) {
    /**
     * Clears the current selection
     */
    fun clearSelection() {
        for (nodeComp in canvasPanel.selectedNodes) {
            nodeComp.isSelected = false
            nodeComp.repaint()
        }
        canvasPanel.selectedNodes.clear()
    }
    
    /**
     * Finalizes the selection process after drawing a selection box
     */
    fun finalizeSelection() {
        if (canvasPanel.selectionStart == null || canvasPanel.selectionEnd == null) return
        
        val rect = getSelectionRectangle()
        val previouslySelectedCount = canvasPanel.selectedNodes.size
        val initiallySelected = HashSet(canvasPanel.selectedNodes)
        
        // Select all nodes that intersect with the selection rectangle
        for (nodeComp in canvasPanel.nodeComponents.values) {
            val nodeBounds = Rectangle(nodeComp.x, nodeComp.y, nodeComp.width, nodeComp.height)
            
            if (rect.intersects(nodeBounds)) {
                canvasPanel.selectedNodes.add(nodeComp)
                nodeComp.isSelected = true
            }
        }
        
        // If selection changed from single to multi or vice versa, repaint all selected nodes
        if ((previouslySelectedCount == 1 && canvasPanel.selectedNodes.size > 1) || 
            (previouslySelectedCount > 1 && canvasPanel.selectedNodes.size == 1)) {
            for (nodeComp in canvasPanel.selectedNodes) {
                nodeComp.repaint()
            }
        } else {
            // Otherwise, just repaint newly selected nodes
            for (nodeComp in canvasPanel.selectedNodes) {
                if (!initiallySelected.contains(nodeComp)) {
                    nodeComp.repaint()
                }
            }
        }
    }
    
    /**
     * Gets the rectangle representing the current selection area
     */
    fun getSelectionRectangle(): Rectangle {
        val x = Math.min(canvasPanel.selectionStart!!.x, canvasPanel.selectionEnd!!.x)
        val y = Math.min(canvasPanel.selectionStart!!.y, canvasPanel.selectionEnd!!.y)
        val width = Math.abs(canvasPanel.selectionEnd!!.x - canvasPanel.selectionStart!!.x)
        val height = Math.abs(canvasPanel.selectionEnd!!.y - canvasPanel.selectionStart!!.y)
        
        return Rectangle(x, y, width, height)
    }
    
    /**
     * Draws the selection box
     */
    fun drawSelectionBox(g: Graphics) {
        val g2d = g.create() as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        val rect = getSelectionRectangle()
        
        // Fill with semi-transparent color
        g2d.color = CanvasColors.SELECTION_BOX_COLOR
        g2d.fill(rect)
        
        // Draw border
        g2d.color = CanvasColors.SELECTION_BOX_BORDER_COLOR
        g2d.stroke = BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2d.draw(rect)
        
        g2d.dispose()
    }
}