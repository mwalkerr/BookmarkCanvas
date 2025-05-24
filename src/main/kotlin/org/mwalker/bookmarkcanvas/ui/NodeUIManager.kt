package org.mwalker.bookmarkcanvas.ui

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.text.JTextComponent
import com.intellij.ui.JBColor
import org.mwalker.bookmarkcanvas.ui.CanvasColors
import org.mwalker.bookmarkcanvas.ui.CanvasConstants
import com.intellij.openapi.util.TextRange
import java.awt.*
import javax.swing.SwingUtilities

/**
 * Manages UI components for a NodeComponent
 */
class NodeUIManager(
    private val nodeComponent: Component,
    private val node: BookmarkNode, 
    private val project: Project
) {
    private val LOG = Logger.getInstance(NodeUIManager::class.java)
    
    // Constants from CanvasConstants
    companion object {
        // Use centralized constants
        private val TITLE_PADDING = CanvasConstants.TITLE_PADDING
        private val CONTENT_PADDING = CanvasConstants.CONTENT_PADDING
        private val BASE_TITLE_FONT_SIZE = CanvasConstants.BASE_TITLE_FONT_SIZE
        private val BASE_CODE_FONT_SIZE = CanvasConstants.BASE_CODE_FONT_SIZE
        
        // Absolute minimum sizes regardless of zoom factor to ensure visibility
        private val MIN_VISIBLE_TITLE_SIZE = CanvasConstants.MIN_VISIBLE_TITLE_SIZE
        private val MIN_VISIBLE_CODE_SIZE = CanvasConstants.MIN_VISIBLE_CODE_SIZE
        
        // Cache for highlighted code snippets to avoid re-rendering
        private val highlightedSnippetCache = mutableMapOf<String, KotlinSnippetHighlighter>()
        
        /**
         * Invalidates the snippet cache for a specific node
         */
        fun invalidateSnippetCache(nodeId: String) {
            val keysToRemove = highlightedSnippetCache.keys.filter { it.startsWith("$nodeId:") }
            keysToRemove.forEach { highlightedSnippetCache.remove(it) }
        }
        
        /**
         * Invalidates the entire snippet cache
         */
        fun clearSnippetCache() {
            highlightedSnippetCache.clear()
        }
    }
    
    // Core UI components
    private lateinit var titleTextPane: JTextPane
    private lateinit var titlePanel: JPanel
    private var codeArea: JBTextArea? = null
    
    // Expose the highlighter as a property that can be accessed from NodeComponent
    var highlightedSnippet: KotlinSnippetHighlighter? = null
        private set
    
    // Generate a cache key for this node's code snippet
    private fun getSnippetCacheKey(): String {
        return "${node.id}:${node.filePath}:${node.lineNumber0Based}:${node.contextLinesBefore}:${node.contextLinesAfter}"
    }
    
    /**
     * Initializes and returns the title panel component
     */
    fun createTitlePanel(): Pair<JPanel, JTextPane> {
        // Title area using JTextPane for better text wrapping
        titleTextPane = object : JTextPane() {
            override fun contains(x: Int, y: Int): Boolean {
                return false // Make transparent to mouse events
            }
            
            // Override UI painting methods to ensure colors are always correct
            override fun setForeground(fg: Color?) {
                super.setForeground(CanvasColors.NODE_TEXT_COLOR)
                document?.putProperty("ForegroundColor", CanvasColors.NODE_TEXT_COLOR)
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
        
        // Wrap in a panel for proper layout with GitHub-style spacing
        titlePanel = JPanel(BorderLayout())
        titlePanel.isOpaque = true
        titlePanel.background = CanvasColors.SELECTION_HEADER_COLOR  // GitHub dark title bg color
        
        // Match GitHub style with more generous padding (8px vertical, 12px horizontal)
        titleTextPane.setBorder(EmptyBorder(0, 0, 0, 0)) // Remove any textPane borders
        
        // Create a compound border with bottom border matching GitHub style
        val bottomBorder = BorderFactory.createMatteBorder(0, 0, 1, 0, CanvasColors.BORDER_COLOR)
        val paddingBorder = BorderFactory.createEmptyBorder(8, 12, 8, 12)
        titlePanel.setBorder(BorderFactory.createCompoundBorder(bottomBorder, paddingBorder))
        
        titlePanel.add(titleTextPane, BorderLayout.CENTER)
        
        return Pair(titlePanel, titleTextPane)
    }
    
    /**
     * Creates and returns the code snippet component
     */
    fun setupCodeSnippetView(parentContainer: JPanel): JPanel {
        // Get the code snippet first
        val code = node.getCodeSnippet(project)
        
        // Calculate content panel size based on code content
        val font = Font("Monospaced", Font.PLAIN, BASE_CODE_FONT_SIZE)
        val frc = getFontRenderContext(parentContainer)
        val codeDimensions = calculateCodeSnippetDimensions(code, font, frc, 600)
        
        // Create content panel first
        val contentPanel = JPanel(BorderLayout())
        contentPanel.background = CanvasColors.SNIPPET_BACKGROUND
        contentPanel.border = EmptyBorder(CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING)
        contentPanel.setSize(codeDimensions.width, codeDimensions.height)
        contentPanel.preferredSize = codeDimensions
        
        // Apply event forwarding to the content panel
        contentPanel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = forwardMouseEvent(contentPanel, e, nodeComponent)
            override fun mouseReleased(e: MouseEvent) = forwardMouseEvent(contentPanel, e, nodeComponent)
            override fun mouseClicked(e: MouseEvent) = forwardMouseEvent(contentPanel, e, nodeComponent)
        })
        
        contentPanel.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) = forwardMouseEvent(contentPanel, e, nodeComponent)
            override fun mouseMoved(e: MouseEvent) = forwardMouseEvent(contentPanel, e, nodeComponent)
        })
        
        // Use the highlighter for any file - the editor will automatically use the appropriate highlighter
        val useHighlighter = true
        
        if (useHighlighter) {
            // Check cache first
            val cacheKey = getSnippetCacheKey()
            val cachedSnippet = highlightedSnippetCache[cacheKey]
            
            if (cachedSnippet != null) {
                LOG.info("Using cached snippet for node: ${node.id}")
                highlightedSnippet = cachedSnippet
                
                // Ensure cached snippet has correct zoom factor applied
                val canvasPanel = SwingUtilities.getAncestorOfClass(CanvasPanel::class.java, nodeComponent)
                val currentZoomFactor = if (canvasPanel != null) (canvasPanel as CanvasPanel).zoomFactor else 1.0
                cachedSnippet.updateZoomFactor(currentZoomFactor)
            } else {
                LOG.info("Creating new snippet highlighter for node: ${node.id}")
                // Create a new highlighter and cache it
                val newHighlighter = KotlinSnippetHighlighter(project)
                highlightedSnippet = newHighlighter
                highlightedSnippetCache[cacheKey] = newHighlighter
                
                // Apply current zoom factor to the new highlighter
                val canvasPanel = SwingUtilities.getAncestorOfClass(CanvasPanel::class.java, nodeComponent)
                val currentZoomFactor = if (canvasPanel != null) (canvasPanel as CanvasPanel).zoomFactor else 1.0
                newHighlighter.updateZoomFactor(currentZoomFactor)
                
                // For proper highlighting, we need the full document content
                val fullCode = getFullDocumentContent(project, node.filePath)
                
                if (fullCode != null) {
                    try {
                        // Determine the range to highlight
                        val startLine = maxOf(0, node.lineNumber0Based - node.contextLinesBefore)
                        
                        // Create a document to calculate offsets
                        val document = com.intellij.openapi.editor.impl.DocumentImpl(fullCode)
                        
                        // Calculate offsets from line numbers
                        val startOffset = if (document.lineCount > startLine) document.getLineStartOffset(startLine) else 0
                        val endLine = minOf(document.lineCount - 1, node.lineNumber0Based + node.contextLinesAfter)
                        val endOffset = if (document.lineCount > endLine) document.getLineEndOffset(endLine) else fullCode.length
                        
                        // Get the file extension for proper syntax highlighting
                        val extension = node.filePath.substringAfterLast('.', "txt")
                        
                        // Display the highlighted snippet with the appropriate file type
                        newHighlighter.displayHighlightedSnippet(fullCode, TextRange(startOffset, endOffset), extension)
                        
                        // Ensure zoom factor is applied after content is displayed
                        newHighlighter.updateZoomFactor(currentZoomFactor)
                    } catch (e: Exception) {
                        LOG.error("Error highlighting snippet", e)
                        newHighlighter.displayPlainText(code)
                        // Apply zoom factor even for plain text
                        newHighlighter.updateZoomFactor(currentZoomFactor)
                    }
                } else {
                    // Fallback to plain text
                    newHighlighter.displayPlainText(code)
                    // Apply zoom factor for plain text too
                    newHighlighter.updateZoomFactor(currentZoomFactor)
                }
            }
            
            // Add mouse event forwarding to the highlighter component as well
            highlightedSnippet?.let { snippet ->
                snippet.addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) = forwardMouseEvent(snippet, e, nodeComponent)
                    override fun mouseReleased(e: MouseEvent) = forwardMouseEvent(snippet, e, nodeComponent)
                    override fun mouseClicked(e: MouseEvent) = forwardMouseEvent(snippet, e, nodeComponent)
                })
                
                snippet.addMouseMotionListener(object : MouseMotionAdapter() {
                    override fun mouseDragged(e: MouseEvent) = forwardMouseEvent(snippet, e, nodeComponent)
                    override fun mouseMoved(e: MouseEvent) = forwardMouseEvent(snippet, e, nodeComponent)
                })
                
                // Add the highlighter to the content panel
                contentPanel.add(snippet, BorderLayout.CENTER)
            }
        } else {
            // Fallback to regular code display with JBTextArea
            val fallbackCodeArea = JBTextArea(code)
            this.codeArea = fallbackCodeArea
            fallbackCodeArea.isEditable = false
            fallbackCodeArea.isEnabled = false  // Prevent selection
            fallbackCodeArea.highlighter = null // Disable highlighting
            fallbackCodeArea.font = Font("Monospaced", Font.PLAIN, 12)
            fallbackCodeArea.background = CanvasColors.SNIPPET_BACKGROUND
            fallbackCodeArea.foreground = CanvasColors.SNIPPET_TEXT_COLOR
            fallbackCodeArea.caretColor = CanvasColors.SNIPPET_TEXT_COLOR
            fallbackCodeArea.lineWrap = true
            fallbackCodeArea.wrapStyleWord = true
            
            // Add the text area to the content panel
            contentPanel.add(fallbackCodeArea, BorderLayout.CENTER)
            
            // Ensure clicks on the text area propagate to the parent for dragging
            fallbackCodeArea.addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) = forwardMouseEvent(fallbackCodeArea, e, nodeComponent)
                override fun mouseReleased(e: MouseEvent) = forwardMouseEvent(fallbackCodeArea, e, nodeComponent)
                override fun mouseClicked(e: MouseEvent) = forwardMouseEvent(fallbackCodeArea, e, nodeComponent)
            })
            
            // Also handle mouse drags
            fallbackCodeArea.addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseDragged(e: MouseEvent) = forwardMouseEvent(fallbackCodeArea, e, nodeComponent)
                override fun mouseMoved(e: MouseEvent) = forwardMouseEvent(fallbackCodeArea, e, nodeComponent)
            })
        }

        return contentPanel
    }
    
    /**
     * Gets the full document content for proper syntax highlighting
     */
    private fun getFullDocumentContent(project: Project, filePath: String): String? {
        return kotlin.runCatching {
            // Handle both absolute paths and project-relative paths
            val file = if (filePath.startsWith("/") || filePath.contains(":\\")) {
                // Absolute path
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath)
            } else {
                // Project-relative path
                project.baseDir.findFileByRelativePath(filePath)
            } ?: return@runCatching null

            val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(file)
                ?: return@runCatching null

            val document = psiFile.viewProvider.document
                ?: return@runCatching null
                
            document.text
        }.getOrNull()
    }
    
    /**
     * Updates font sizes based on the provided zoom factor
     */
    fun updateFontSizes(zoomFactor: Double) {
        // Update title font - scale proportionally with zoom factor (no minimum size constraint)
        val scaledTitleSize = (BASE_TITLE_FONT_SIZE * zoomFactor).toInt()
        
        // Get current font and ensure we maintain the style but update the size
        val currentFont = titleTextPane.font
        val newFont = currentFont.deriveFont(Font.BOLD, scaledTitleSize.toFloat())
        
        // Apply the new font and make sure colors are set properly
        titleTextPane.font = newFont
        titleTextPane.foreground = CanvasColors.NODE_TEXT_COLOR
        titleTextPane.document.putProperty("ForegroundColor", CanvasColors.NODE_TEXT_COLOR)
        
        // Ensure title panel background color is correctly set too
        titlePanel.background = CanvasColors.SELECTION_HEADER_COLOR
        
        // After adjusting font, ensure it's visible by forcing display update
        titleTextPane.invalidate()
        titleTextPane.validate()
        
        // Update code area font if present (legacy support)
        codeArea?.let { area ->
            val scaledCodeSize = (BASE_CODE_FONT_SIZE * zoomFactor).toInt()
            val newCodeFont = Font("Monospaced", Font.PLAIN, scaledCodeSize)
            area.font = newCodeFont
            area.foreground = CanvasColors.SNIPPET_TEXT_COLOR // Ensure text color is visible
            
            // Force the code area to update as well
            area.invalidate()
            area.validate()
        }
        
        // Update highlighted snippet if present
        highlightedSnippet?.let { snippet ->
            // The snippet will use its own internal zoom updating mechanism
            snippet.updateZoomFactor(zoomFactor)
            snippet.invalidate()
            snippet.validate()
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
            // For code snippet mode, calculate size based on actual content
            val code = node.getCodeSnippet(project)
            val font = Font("Monospaced", Font.PLAIN, BASE_CODE_FONT_SIZE)
            val frc = getFontRenderContext(parentComponent)
            
            // Calculate dimensions based on code content with reasonable max width
            val codeDimensions = calculateCodeSnippetDimensions(code, font, frc, 600)
            
            // Add title height to the total height
            val titleHeight = TITLE_PADDING * 2 + 30 // Approximate title height
            
            return Dimension(
                codeDimensions.width,
                codeDimensions.height + titleHeight
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
     * Updates the title text and ensures colors are properly applied
     */
    fun updateTitle(title: String) {
        titleTextPane.text = title
        
        // Ensure colors are properly applied
        titleTextPane.foreground = CanvasColors.NODE_TEXT_COLOR
        titleTextPane.document.putProperty("ForegroundColor", CanvasColors.NODE_TEXT_COLOR)
        
        // Make sure title panel has proper background color
        titlePanel.background = CanvasColors.SELECTION_HEADER_COLOR
    }
}