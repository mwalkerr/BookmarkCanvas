package org.mwalker.bookmarkcanvas.ui

import java.awt.BasicStroke
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints

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
        
        // Select all nodes that intersect with the selection rectangle
        for (nodeComp in canvasPanel.nodeComponents.values) {
            val nodeBounds = Rectangle(nodeComp.x, nodeComp.y, nodeComp.width, nodeComp.height)
            
            if (rect.intersects(nodeBounds)) {
                canvasPanel.selectedNodes.add(nodeComp)
                nodeComp.isSelected = true
                nodeComp.repaint()
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
        g2d.color = CanvasPanel.SELECTION_BOX_COLOR
        g2d.fill(rect)
        
        // Draw border
        g2d.color = CanvasPanel.SELECTION_BOX_BORDER_COLOR
        g2d.stroke = BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2d.draw(rect)
        
        g2d.dispose()
    }
}