package org.mwalker.bookmarkcanvas.ui

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.text.JTextComponent
import javax.swing.text.View

/**
 * A UI component that represents a bookmark node on the canvas.
 * 
 * This class has been refactored to delegate functionality to specialized helper classes:
 * - NodeUIManager: handles UI components creation and management
 * - NodeContextMenuManager: handles context menu creation and actions
 * - NodeEventHandler: handles mouse and keyboard event handling
 */
class NodeComponent(val node: BookmarkNode, private val project: Project) : 
    JPanel(), NodeContextMenuManager.NodeComponentInternal, NodeEventHandler.NodeComponentInternal {
    
    private val LOG = Logger.getInstance(NodeComponent::class.java)
    
    // Constants
    companion object {
        private const val RESIZE_HANDLE_SIZE = 10
        private const val TITLE_PADDING = 8
        private const val CONTENT_PADDING = 10
    }
    
    // Core UI components
    private lateinit var titleTextPane: JTextPane
    private lateinit var titlePanel: JPanel
    private val titleLabel: JBLabel // Kept for compatibility with existing code
    
    // Helper managers
    private val uiManager: NodeUIManager
    private val contextMenuManager: NodeContextMenuManager
    private val eventHandler: NodeEventHandler
    
    // State
    var isSelected = false
        set(value) {
            if (field != value) {
                field = value
                repaint()
            }
        }

    init {
        // Basic panel setup
        layout = BorderLayout()
        border = CompoundBorder(
            LineBorder(JBColor.border(), 1, true),
            EmptyBorder(TITLE_PADDING, CONTENT_PADDING, TITLE_PADDING, CONTENT_PADDING)
        )
        background = UIColors.NODE_BACKGROUND
        
        // Initialize UI manager and create title components
        uiManager = NodeUIManager(this, node, project)
        val (titleComp, textPane) = uiManager.createTitlePanel()
        titlePanel = titleComp
        titleTextPane = textPane
        
        // Store reference as titleLabel for compatibility with existing code
        titleLabel = object : JBLabel() {
            override fun contains(x: Int, y: Int): Boolean {
                return false
            }
        }.apply {
            isVisible = false // Not actually used for display
        }
        
        // Add title panel with bottom padding when code snippet is shown
        if (node.showCodeSnippet) {
            titlePanel.border = EmptyBorder(0, 0, 8, 0) // Add padding below title
        }
        add(titlePanel, BorderLayout.NORTH)

        // Initialize code snippet if needed
        if (node.showCodeSnippet) {
            val scrollPane = uiManager.setupCodeSnippetView(this)
            add(scrollPane, BorderLayout.CENTER)
        }

        // Create context menu
        contextMenuManager = NodeContextMenuManager(this, node, project, titlePanel)
        val menu = contextMenuManager.createContextMenu()
        
        // Initialize event handler
        eventHandler = NodeEventHandler(this, node, project, menu, RESIZE_HANDLE_SIZE)
        eventHandler.setupDragBehavior()
        eventHandler.setupKeyboardNavigation()

        // Set size based on content
        val size = uiManager.updatePreferredSize(this)
        setSize(size)
        preferredSize = size
    }
    
    /**
     * Updates font sizes based on the current zoom factor
     */
    fun updateFontSizes(zoomFactor: Double) {
        uiManager.updateFontSizes(zoomFactor)
        revalidate()
        repaint()
    }
    
    /**
     * Updates the node title
     */
    override fun updateTitle(title: String) {
        uiManager.updateTitle(title)
        
        // Recalculate size based on new text
        val size = uiManager.updatePreferredSize(this)
        setSize(size)
        preferredSize = size
        
        // Update UI
        titlePanel.revalidate()
        revalidate()
        repaint()
    }
    
    /**
     * Refreshes the component layout when content changes
     */
    override fun refreshLayout() {
        // Remove existing components
        removeAll()
        
        // Set title panel border based on whether code snippet is shown
        if (node.showCodeSnippet) {
            titlePanel.border = EmptyBorder(0, 0, 8, 0) // Add padding below title
        } else {
            titlePanel.border = EmptyBorder(0, 0, 0, 0) // Remove padding when no code snippet
        }
        
        add(titlePanel, BorderLayout.NORTH)

        // Re-add code area if needed
        if (node.showCodeSnippet) {
            val scrollPane = uiManager.setupCodeSnippetView(this)
            add(scrollPane, BorderLayout.CENTER)
            preferredSize = Dimension(250, 200)
            setSize(250, 200)
        } else {
            // Update size based on title only
            val size = uiManager.updatePreferredSize(this)
            setSize(size)
            preferredSize = size
        }

        // Update component size
        revalidate()
        repaint()
        
        // Also update the node bounds in the parent
        val parent = parent
        if (parent != null) {
            parent.invalidate()
            parent.validate()
            parent.repaint()
        }
    }
    
    /**
     * Handles resize events from NodeEventHandler
     */
    override fun handleResize(newWidth: Int) {
        // Only recalculate title for non-code snippet nodes
        if (!node.showCodeSnippet) {
            uiManager.adjustTitleForNewWidth(newWidth)
        }
    }
    
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g.create() as Graphics2D
        
        // Set rendering hints for smoother lines
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        // Draw selection border and header if selected
        if (isSelected) {
            // Draw title background highlight
            val headerRect = Rectangle(0, 0, width, titlePanel.height + TITLE_PADDING)
            g2d.color = UIColors.SELECTION_HEADER_COLOR
            g2d.fill(headerRect)
            
            // Draw selection border
            g2d.color = UIColors.SELECTION_BORDER_COLOR
            g2d.stroke = BasicStroke(2.0f)
            g2d.drawRect(1, 1, width - 3, height - 3)
        }
        
        // Draw resize handle
        drawResizeHandle(g2d, width, height, RESIZE_HANDLE_SIZE, UIColors.RESIZE_HANDLE_COLOR)
        
        g2d.dispose()
    }
}