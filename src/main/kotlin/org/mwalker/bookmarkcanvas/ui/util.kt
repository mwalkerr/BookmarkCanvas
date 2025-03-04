package org.mwalker.bookmarkcanvas.ui

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
 * Utility class to throttle frequent events like mouse movements and drags
 * to improve performance by limiting how often an operation is executed.
 */
class EventThrottler(private val delayMs: Long = 16) { // ~60fps by default
    private var lastExecutionTime: Long = 0
    private var pendingActionScheduled = false
    private var pendingAction: (() -> Unit)? = null
    
    /**
     * Throttles the execution of the provided action
     * @param action The action to execute after throttling
     * @return true if action was executed immediately, false if throttled
     */
    fun throttle(action: () -> Unit): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // If enough time has passed since last execution, run immediately
        if (currentTime - lastExecutionTime >= delayMs) {
            action()
            lastExecutionTime = currentTime
            return true
        }
        
        // Store the latest action
        pendingAction = action
        
        // If we've already scheduled an action, don't schedule another one
        if (pendingActionScheduled) {
            return false
        }
        
        // Mark that we've scheduled an action
        pendingActionScheduled = true
        
        // Schedule for later execution - only one invokeLater per throttle window
        SwingUtilities.invokeLater { 
            // Reset the scheduled flag
            pendingActionScheduled = false
            
            // Get the latest action and clear the reference
            pendingAction?.let { 
                it()
                lastExecutionTime = System.currentTimeMillis()
            }
            
            // Clear the pending action
            pendingAction = null
        }
        
        return false
    }
    
    /**
     * Clear any pending throttled actions
     */
    fun clear() {
        pendingAction = null
        // We don't cancel the scheduled invokeLater, but it will do nothing
        // since pendingAction will be null when it runs
    }
}