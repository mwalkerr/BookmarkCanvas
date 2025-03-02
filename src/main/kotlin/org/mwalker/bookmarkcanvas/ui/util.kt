package org.mwalker.bookmarkcanvas.ui

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.font.FontRenderContext
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

/**
 * Forwards a mouse event from one component to another, converting coordinates.
 */
fun forwardMouseEvent(src: Component, e: MouseEvent, target: Component) {
    // Convert coordinates from source to target component
    val parentEvent = SwingUtilities.convertMouseEvent(src, e, target)
    
    // Dispatch the new event to the target
    target.dispatchEvent(parentEvent)
    
    // Consume the original event to prevent default handling
    e.consume()
}

/**
 * Calculates text height using LineBreakMeasurer for accurate line wrapping
 */
fun calculateTextHeight(text: String, font: Font, width: Int, frc: FontRenderContext): Int {
    if (text.isEmpty()) return 20
    
    val attributedString = java.text.AttributedString(text)
    attributedString.addAttribute(java.awt.font.TextAttribute.FONT, font)
    
    val iterator = attributedString.iterator
    val measurer = java.awt.font.LineBreakMeasurer(iterator, frc)
    
    var y = 0
    measurer.position = 0
    // Calculate height by measuring each line
    while (measurer.position < text.length) {
        val layout = measurer.nextLayout(width.toFloat())
        y += ((layout.ascent + layout.descent + layout.leading) * 1.5).toInt()
    }
    
    return y.coerceAtLeast(20) // Minimum height of 20 pixels
}

/**
 * Gets FontRenderContext from a component or creates a default one if not available
 */
fun getFontRenderContext(component: Component): FontRenderContext {
    return (component.getGraphics() as? Graphics2D)?.fontRenderContext 
        ?: FontRenderContext(null, true, true)
}

/**
 * Checks if a point is within the resize area of a component with the specified handle size
 */
fun isInResizeArea(point: Point, componentWidth: Int, componentHeight: Int, handleSize: Int): Boolean {
    val resizeArea = Rectangle(
        componentWidth - handleSize, 
        componentHeight - handleSize,
        handleSize,
        handleSize
    )
    return resizeArea.contains(point)
}

/**
 * Draws a resize handle in the bottom-right corner of a component
 */
fun drawResizeHandle(g2d: Graphics2D, componentWidth: Int, componentHeight: Int, handleSize: Int, color: Color) {
    g2d.color = color
    
    // Draw diagonal lines for resize handle
    val x = componentWidth - handleSize
    val y = componentHeight - handleSize
    for (i in 1..3) {
        val offset = i * 3
        g2d.drawLine(
            x + offset, y + handleSize,
            x + handleSize, y + offset
        )
    }
}

/**
 * Common UI colors for consistent appearance across components
 */
object UIColors {
    val NODE_BACKGROUND = JBColor(
        Color(250, 250, 250), // Light mode
        Color(43, 43, 43),    // Dark mode
    )
    val NODE_TEXT_COLOR = JBColor(
        Color(0, 0, 0),        // Light mode
        Color(187, 187, 187),  // Dark mode
    )
    val RESIZE_HANDLE_COLOR = JBColor(
        Color(180, 180, 180),  // Light mode
        Color(100, 100, 100),  // Dark mode
    )
    val SELECTION_BORDER_COLOR = JBColor(
        Color(0, 120, 215),    // Light mode
        Color(75, 110, 175)    // Dark mode
    )
    val SELECTION_HEADER_COLOR = JBColor(
        Color(210, 230, 255),  // Light mode
        Color(45, 65, 100)     // Dark mode
    )
}