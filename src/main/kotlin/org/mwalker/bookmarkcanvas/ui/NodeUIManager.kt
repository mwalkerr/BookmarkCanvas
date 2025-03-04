package org.mwalker.bookmarkcanvas.ui

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.text.JTextComponent
import com.intellij.ui.JBColor
import org.mwalker.bookmarkcanvas.ui.CanvasColors

/**
 * Manages UI components for a NodeComponent
 */
class NodeUIManager(
    private val nodeComponent: Component,
    private val node: BookmarkNode, 
    private val project: Project
) {
    private val LOG = Logger.getInstance(NodeUIManager::class.java)
    
    // Constants
    companion object {
        private const val TITLE_PADDING = 8
        private const val CONTENT_PADDING = 10
        private const val BASE_TITLE_FONT_SIZE = 12
        private const val BASE_CODE_FONT_SIZE = 12
        
        // Absolute minimum sizes regardless of zoom factor to ensure visibility
        private const val MIN_VISIBLE_TITLE_SIZE = 6
        private const val MIN_VISIBLE_CODE_SIZE = 6
    }
    
    // Core UI components
    private lateinit var titleTextPane: JTextPane
    private lateinit var titlePanel: JPanel
    private var codeArea: JBTextArea? = null
    
    /**
     * Initializes and returns the title panel component
     */
    fun createTitlePanel(): Pair<JPanel, JTextPane> {
        // Title area using JTextPane for better text wrapping
        titleTextPane = object : JTextPane() {
            override fun contains(x: Int, y: Int): Boolean {
                return false // Make transparent to mouse events
            }
        }.apply {
            text = node.getDisplayText()
            foreground = CanvasColors.NODE_TEXT_COLOR
            document.putProperty("ForegroundColor", CanvasColors.NODE_TEXT_COLOR)
            font = font.deriveFont(Font.BOLD)
            isEditable = false
            isOpaque = false
            border = EmptyBorder(0, 0, 0, 0)
            // Disable text selection
            highlighter = null
            // Set size directly to ensure wrapping
            setSize(250, 30) // Minimum height to ensure text is visible
        }
        
        // Wrap in a panel for proper layout
        titlePanel = JPanel(BorderLayout())
        titlePanel.isOpaque = false
        titlePanel.add(titleTextPane, BorderLayout.CENTER)
        
        return Pair(titlePanel, titleTextPane)
    }
    
    /**
     * Creates and returns the code snippet component
     */
    fun setupCodeSnippetView(parentContainer: JPanel): JBScrollPane {
        val code = node.getCodeSnippet(project)
        val newCodeArea = JBTextArea(code)
        this.codeArea = newCodeArea

        newCodeArea.isEditable = false
        newCodeArea.isEnabled = false  // Prevent selection
        newCodeArea.highlighter = null // Disable highlighting
        newCodeArea.font = Font("Monospaced", Font.PLAIN, 12)
        newCodeArea.background = CanvasColors.SNIPPET_BACKGROUND
        newCodeArea.foreground = CanvasColors.SNIPPET_TEXT_COLOR
        newCodeArea.caretColor = CanvasColors.SNIPPET_TEXT_COLOR

        // Ensure clicks on the text area propagate to the parent for dragging
        newCodeArea.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = forwardMouseEvent(newCodeArea, e, nodeComponent)
            override fun mouseReleased(e: MouseEvent) = forwardMouseEvent(newCodeArea, e, nodeComponent)
            override fun mouseClicked(e: MouseEvent) = forwardMouseEvent(newCodeArea, e, nodeComponent)
        })
        
        // Also handle mouse drags
        newCodeArea.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) = forwardMouseEvent(newCodeArea, e, nodeComponent)
            override fun mouseMoved(e: MouseEvent) = forwardMouseEvent(newCodeArea, e, nodeComponent)
        })
        
        // Create scroll pane
        val scrollPane = JBScrollPane(newCodeArea)
        scrollPane.setSize(200, 150)
        scrollPane.preferredSize = Dimension(200, 150) // Keep for layout compatibility
        scrollPane.border = LineBorder(CanvasColors.BORDER_COLOR, 1)
        
        // Also apply the same event forwarding to the scroll pane
        scrollPane.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = forwardMouseEvent(scrollPane, e, nodeComponent)
            override fun mouseReleased(e: MouseEvent) = forwardMouseEvent(scrollPane, e, nodeComponent)
            override fun mouseClicked(e: MouseEvent) = forwardMouseEvent(scrollPane, e, nodeComponent)
        })
        
        scrollPane.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) = forwardMouseEvent(scrollPane, e, nodeComponent)
            override fun mouseMoved(e: MouseEvent) = forwardMouseEvent(scrollPane, e, nodeComponent)
        })

        return scrollPane
    }
    
    /**
     * Updates font sizes based on the provided zoom factor
     */
    fun updateFontSizes(zoomFactor: Double) {
        // Update title font - ensure minimum size is proportional to zoom
        // but never below an absolute minimum to maintain visibility
        val scaledTitleSize = (BASE_TITLE_FONT_SIZE * zoomFactor).toInt().coerceAtLeast(MIN_VISIBLE_TITLE_SIZE)
        
        // Get current font and ensure we maintain the style but update the size
        val currentFont = titleTextPane.font
        val newFont = currentFont.deriveFont(Font.BOLD, scaledTitleSize.toFloat())
        
        // Apply the new font and make sure color is set properly
        titleTextPane.font = newFont
        titleTextPane.foreground = CanvasColors.NODE_TEXT_COLOR
        titleTextPane.document.putProperty("ForegroundColor", CanvasColors.NODE_TEXT_COLOR)
        
        // After adjusting font, ensure it's visible by forcing display update
        titleTextPane.invalidate()
        titleTextPane.validate()
        
        // Update code area font if present
        codeArea?.let { area ->
            val scaledCodeSize = (BASE_CODE_FONT_SIZE * zoomFactor).toInt().coerceAtLeast(MIN_VISIBLE_CODE_SIZE)
            val newCodeFont = Font("Monospaced", Font.PLAIN, scaledCodeSize)
            area.font = newCodeFont
            area.foreground = CanvasColors.SNIPPET_TEXT_COLOR // Ensure text color is visible
            
            // Force the code area to update as well
            area.invalidate()
            area.validate()
        }
    }
    
    /**
     * Updates the size of the node based on its content
     */
    fun updatePreferredSize(parentComponent: JPanel): Dimension {
        LOG.info("Updating size for node: ${node.getDisplayText()}, showCodeSnippet: ${node.showCodeSnippet}")
        
        if (!node.showCodeSnippet) {
            // Calculate text height using LineBreakMeasurer for accurate wrapping
            val displayText = node.getDisplayText()
            // Use the current component width if it's valid
            val effectiveWidth = if (parentComponent.width > 0) 
                                     parentComponent.width - (CONTENT_PADDING * 2) 
                                 else 
                                     250 - (CONTENT_PADDING * 2)
            
            // Calculate text height with line break measurer
            val textHeight = calculateTextHeight(
                displayText, 
                titleTextPane.font, 
                effectiveWidth,
                getFontRenderContext(parentComponent)
            )
            
            // Add padding to text height
            val totalHeight = textHeight + (TITLE_PADDING * 2) + 10
            
            // Set size directly for title pane
            val titleSize = Dimension(effectiveWidth, textHeight.coerceAtLeast(20))
            titleTextPane.setSize(titleSize)
            titleTextPane.preferredSize = titleSize

            // Return new size - preserve the current width to avoid shrinking after resize
            return Dimension(
                parentComponent.width.takeIf { it > 0 } ?: (effectiveWidth + (CONTENT_PADDING * 2)), 
                totalHeight.coerceAtLeast(40)
            )
        } else {
            // For code snippet mode, preserve width during recalculation
            return Dimension(
                parentComponent.width.takeIf { it > 0 } ?: 250, 
                200
            )
        }
    }
    
    /**
     * Adjusts title text wrapping when node width changes
     */
    fun adjustTitleForNewWidth(width: Int) {
        if (width <= 0) return
        
        val effectiveWidth = width - (CONTENT_PADDING * 2)
        
        // Update title pane size to match new width
        val newTitleSize = Dimension(
            effectiveWidth,
            titleTextPane.height
        )
        
        titleTextPane.setSize(newTitleSize)
        titleTextPane.preferredSize = newTitleSize
        
        // Recalculate text height for new width
        val textHeight = calculateTextHeight(
            node.getDisplayText(),
            titleTextPane.font,
            effectiveWidth,
            getFontRenderContext(titleTextPane)
        )
        
        // Apply the new height
        val updatedTitleSize = Dimension(effectiveWidth, textHeight.coerceAtLeast(20))
        titleTextPane.preferredSize = updatedTitleSize
        titleTextPane.setSize(updatedTitleSize)
        
        titlePanel.revalidate()
    }
    
    /**
     * Updates the title text
     */
    fun updateTitle(title: String) {
        titleTextPane.text = title
        titleTextPane.foreground = CanvasColors.NODE_TEXT_COLOR
        titleTextPane.document.putProperty("ForegroundColor", CanvasColors.NODE_TEXT_COLOR)
    }
}