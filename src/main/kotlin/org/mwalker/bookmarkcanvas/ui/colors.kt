package org.mwalker.bookmarkcanvas.ui

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Centralized color definitions for the Bookmark Canvas
 */
object CanvasColors {
    // Canvas colors
    val CANVAS_BACKGROUND = JBColor(
        Color(240, 240, 240), // Light mode
        Color(30, 30, 30) // Dark mode
    )
    val GRID_BACKGROUND = JBColor(
        Color(245, 245, 245), // Light mode
        Color(30, 30, 30) // Dark mode
    )
    val GRID_COLOR = JBColor(
        Color(210, 210, 210), // Light mode
        Color(50, 50, 50) // Dark mode
    )
    val SELECTION_BOX_COLOR = JBColor(
        Color(100, 150, 255, 50), // Light mode with transparency
        Color(80, 120, 200, 50) // Dark mode with transparency
    )
    val SELECTION_BOX_BORDER_COLOR = JBColor(
        Color(70, 130, 230), // Light mode
        Color(100, 150, 230) // Dark mode
    )

    // Node colors
    val NODE_BACKGROUND = JBColor(
        Color(250, 250, 250), // Light mode
        Color(43, 43, 43),    // Dark mode
    )
    val NODE_TEXT_COLOR = JBColor(
        Color(0, 0, 0),        // Light mode
        Color(187, 187, 187),  // Dark mode
    )
    val SNIPPET_BACKGROUND = JBColor(
        Color(245, 245, 245), // Light mode
        Color(48, 48, 48),    // Dark mode
    )
    val SNIPPET_TEXT_COLOR = JBColor(
        Color(20, 20, 20),      // Light mode
        Color(200, 200, 200),   // Dark mode
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

    // Connection colors
    val CONNECTION_COLOR = JBColor.GRAY
    
    // Border colors
    val BORDER_COLOR = JBColor.border()
}