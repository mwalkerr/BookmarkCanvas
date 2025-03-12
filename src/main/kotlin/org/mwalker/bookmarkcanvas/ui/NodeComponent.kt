package org.mwalker.bookmarkcanvas.ui

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import org.mwalker.bookmarkcanvas.ui.CanvasColors
import org.mwalker.bookmarkcanvas.ui.CanvasConstants
import com.intellij.ui.components.JBLabel
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.text.JTextComponent
import javax.swing.text.View
import javax.swing.SwingUtilities
import javax.swing.RepaintManager

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
    
    // Constants from CanvasConstants
    companion object {
        // Use centralized constants
        private val RESIZE_HANDLE_SIZE = CanvasConstants.RESIZE_HANDLE_SIZE
        private val TITLE_PADDING = CanvasConstants.TITLE_PADDING
        private val CONTENT_PADDING = CanvasConstants.CONTENT_PADDING
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
                // Propagate selection to the highlighter if it exists
                uiManager.highlightedSnippet?.setSelected(value)
                repaint()
            }
        }
    
    // Track if the node is part of a group selection
    var isPartOfSelectionGroup = false
        get() {
            // Only compute this if the node is actually selected
            if (!isSelected) return false
            
            // Cache the parent panel to avoid repeated lookups
            val parentRef = SwingUtilities.getAncestorOfClass(CanvasPanel::class.java, this)
            if (parentRef == null) return false
            
            val panel = parentRef as CanvasPanel
            return panel.selectedNodes.size > 1
        }

    init {
        // Basic panel setup
        layout = BorderLayout()
        border = CompoundBorder(
            LineBorder(CanvasColors.BORDER_COLOR, 2, true),
            EmptyBorder(0, 0, 0, 0)
        )
        background = CanvasColors.NODE_BACKGROUND
        
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
        
        // No need to add additional padding to title panel anymore as it's handled in NodeUIManager
        add(titlePanel, BorderLayout.NORTH)

        // Initialize code snippet if needed
        if (node.showCodeSnippet) {
            val contentPanel = uiManager.setupCodeSnippetView(this)
            add(contentPanel, BorderLayout.CENTER)
        }

        // Create context menu
        contextMenuManager = NodeContextMenuManager(this, node, project, titlePanel)
        val menu = contextMenuManager.createContextMenu()
        
        // Initialize event handler
        eventHandler = NodeEventHandler(this, node, project, menu, RESIZE_HANDLE_SIZE)
        eventHandler.setupDragBehavior()
        eventHandler.setupKeyboardNavigation()

        // Set size based on persisted value or content
        if (node.width > 0 && node.height > 0) {
            // Get the canvas panel to access zoom factor
            val canvasPanel = SwingUtilities.getAncestorOfClass(CanvasPanel::class.java, this)
            val zoomFactor = if (canvasPanel != null) (canvasPanel as CanvasPanel).zoomFactor else 1.0
            
            // Apply zoom factor to stored dimensions
            val scaledWidth = (node.width * zoomFactor).toInt()
            val scaledHeight = (node.height * zoomFactor).toInt()
            val size = Dimension(scaledWidth, scaledHeight)
            setSize(size)
            preferredSize = size
        } else {
            val size = uiManager.updatePreferredSize(this)
            setSize(size)
            preferredSize = size
        }
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
        
        // Use persisted size if available, otherwise recalculate
        if (node.width > 0 && node.height > 0) {
            // Keep the persisted size, but ensure it's properly scaled
            val canvasPanel = SwingUtilities.getAncestorOfClass(CanvasPanel::class.java, this)
            val zoomFactor = if (canvasPanel != null) (canvasPanel as CanvasPanel).zoomFactor else 1.0
            
            // Apply zoom factor to stored dimensions
            val scaledWidth = (node.width * zoomFactor).toInt()
            val scaledHeight = (node.height * zoomFactor).toInt()
            
            setSize(scaledWidth, scaledHeight)
            preferredSize = Dimension(scaledWidth, scaledHeight)
        } else {
            // Recalculate size based on new text
            val size = uiManager.updatePreferredSize(this)
            setSize(size)
            preferredSize = size
        }
        
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
        
        // Title panel padding is now handled in NodeUIManager
        add(titlePanel, BorderLayout.NORTH)

        // Get the canvas panel to access zoom factor
        val canvasPanel = SwingUtilities.getAncestorOfClass(CanvasPanel::class.java, this)
        val zoomFactor = if (canvasPanel != null) (canvasPanel as CanvasPanel).zoomFactor else 1.0
        
        // Re-add code area if needed
        if (node.showCodeSnippet) {
            val contentPanel = uiManager.setupCodeSnippetView(this)
            add(contentPanel, BorderLayout.CENTER)
            
            // Use persisted size if available, or default size for code snippet
            if (node.width > 0 && node.height > 0) {
                // Apply zoom factor to stored dimensions
                val scaledWidth = (node.width * zoomFactor).toInt()
                val scaledHeight = (node.height * zoomFactor).toInt()
                preferredSize = Dimension(scaledWidth, scaledHeight)
                setSize(scaledWidth, scaledHeight)
            } else {
                // Default size for code snippet
                val defaultWidth = (250 * zoomFactor).toInt()
                val defaultHeight = (200 * zoomFactor).toInt()
                preferredSize = Dimension(defaultWidth, defaultHeight)
                setSize(defaultWidth, defaultHeight)
            }
        } else {
            // Use persisted size if available, or calculate based on title
            if (node.width > 0 && node.height > 0) {
                // Apply zoom factor to stored dimensions
                val scaledWidth = (node.width * zoomFactor).toInt()
                val scaledHeight = (node.height * zoomFactor).toInt()
                preferredSize = Dimension(scaledWidth, scaledHeight)
                setSize(scaledWidth, scaledHeight)
            } else {
                val size = uiManager.updatePreferredSize(this)
                setSize(size)
                preferredSize = size
            }
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
    
    // Cache borders to avoid recreating them on every paint
    private val normalBorder = CompoundBorder(
        LineBorder(CanvasColors.BORDER_COLOR, 2, true),
        EmptyBorder(0, 0, 0, 0)
    )
    private val selectionBorder = CompoundBorder(
        LineBorder(CanvasColors.SELECTION_BORDER_COLOR, 2, true),
        EmptyBorder(0, 0, 0, 0)
    )
    private val groupSelectionBorder = CompoundBorder(
        LineBorder(CanvasColors.GROUP_SELECTION_BORDER_COLOR, 2, true),
        EmptyBorder(0, 0, 0, 0)
    )
    private val invalidBookmarkBorder = CompoundBorder(
        LineBorder(CanvasColors.INVALID_BOOKMARK_BORDER_COLOR, 2, true),
        EmptyBorder(0, 0, 0, 0)
    )
    
    // Last known selection state to avoid unnecessary border changes
    private var lastKnownSelectionState = false
    private var lastKnownGroupSelectionState = false
    private var lastKnownValidState = true
    
    /**
     * Paint the component background and borders
     */
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g.create() as Graphics2D
        
        // Set rendering hints for smoother lines
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        // Check if bookmark validity changed
        val currentValidState = node.isValidBookmark
        val validStateChanged = currentValidState != lastKnownValidState
        
        // Only update the border if selection state or validity has changed
        val isPartOfGroup = isPartOfSelectionGroup
        if (isSelected != lastKnownSelectionState || 
            (isSelected && isPartOfGroup != lastKnownGroupSelectionState) ||
            validStateChanged) {
            
            // Update border based on selection state and validity
            if (isSelected) {
                // Selection takes precedence - choose border based on whether this is part of a multi-node selection
                border = if (isPartOfGroup) {
                    groupSelectionBorder
                } else {
                    selectionBorder
                }
                
                // Draw additional highlight
                g2d.color = if (isPartOfGroup) {
                    CanvasColors.GROUP_SELECTION_BORDER_COLOR
                } else {
                    CanvasColors.SELECTION_BORDER_COLOR
                }
                g2d.stroke = BasicStroke(4.0f)
                g2d.drawRect(1, 1, width - 3, height - 3)
            } else if (!currentValidState) {
                // Invalid bookmark - show red border
                border = invalidBookmarkBorder
            } else {
                // Normal unselected state
                border = normalBorder
            }
            
            // Update cached states
            lastKnownSelectionState = isSelected
            lastKnownGroupSelectionState = isPartOfGroup
            lastKnownValidState = currentValidState
        } else if (isSelected) {
            // Always draw the highlight for selected nodes
            g2d.color = if (isPartOfGroup) {
                CanvasColors.GROUP_SELECTION_BORDER_COLOR
            } else {
                CanvasColors.SELECTION_BORDER_COLOR
            }
            g2d.stroke = BasicStroke(4.0f)
            g2d.drawRect(1, 1, width - 3, height - 3)
        } else if (!currentValidState) {
            // Always draw special border for invalid nodes
            border = invalidBookmarkBorder
        }
        
        g2d.dispose()
    }
    
    /**
     * Override to paint the resize handle AFTER all children are painted
     * This ensures the resize handle is always visible on top of all content
     */
    override fun paintChildren(g: Graphics) {
        super.paintChildren(g)
        
        // Create a new graphics object for the resize handle
        val g2d = g.create() as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        // Now draw the resize handle on top of everything else
        // Always use pure white for maximum visibility
        drawResizeHandle(g2d, width, height, RESIZE_HANDLE_SIZE, Color.WHITE)
        
        g2d.dispose()
    }
}