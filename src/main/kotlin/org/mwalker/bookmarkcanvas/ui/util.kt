package org.mwalker.bookmarkcanvas.ui

import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

fun forwardMouseEvent(src: Component, e: MouseEvent, target: Component) {
    // Convert coordinates from source to target component
    val parentEvent = SwingUtilities.convertMouseEvent(src, e, target)
    
    // Dispatch the new event to the target
    target.dispatchEvent(parentEvent)
    
    // Consume the original event to prevent default handling
    e.consume()
}