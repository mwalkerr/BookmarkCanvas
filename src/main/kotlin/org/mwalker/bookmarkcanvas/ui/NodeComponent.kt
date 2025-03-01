package org.mwalker.bookmarkcanvas.ui

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

class NodeComponent(val node: BookmarkNode, private val project: Project) : JPanel() {
    private val titleLabel: JBLabel
    private var codeArea: JBTextArea? = null
    private var dragStart: Point? = null
    private var isDragging = false

    companion object {
        private val NODE_BACKGROUND = JBColor(
            Color(250, 250, 250), // Light mode
            Color(43, 43, 43), // Dark mode
        )
        private val NODE_TEXT_COLOR = JBColor(
            Color(0, 0, 0), // Light mode
            Color(187, 187, 187), // Dark mode
        )
        private val TITLE_PADDING = 8
        private val CONTENT_PADDING = 10
    }

    init {
        layout = BorderLayout()
        border = CompoundBorder(
            LineBorder(JBColor.border(), 1, true),
            EmptyBorder(TITLE_PADDING, CONTENT_PADDING, TITLE_PADDING, CONTENT_PADDING)
        )
        background = NODE_BACKGROUND

        // Title area
        titleLabel = JBLabel(node.displayName).apply {
            foreground = NODE_TEXT_COLOR
            font = font.deriveFont(Font.BOLD)
        }
        add(titleLabel, BorderLayout.NORTH)

        // Code snippet area (initially hidden)
        if (node.showCodeSnippet) {
            setupCodeSnippetView()
        }

        // Setup context menu and actions
        setupContextMenu()

        // Setup dragging behavior
        setupDragBehavior()

        // Setup keyboard navigation
        setupKeyboardNavigation()

        // Set preferred size based on content
        updatePreferredSize()
    }
    
    private fun updatePreferredSize() {
        if (!node.showCodeSnippet) {
            val fontMetrics = titleLabel.getFontMetrics(titleLabel.font)
            val textWidth = fontMetrics.stringWidth(titleLabel.text)
            val textHeight = fontMetrics.height
            
            val width = textWidth + (CONTENT_PADDING * 2) + 20 // Extra padding for better appearance
            val height = textHeight + (TITLE_PADDING * 2) + 10
            
            preferredSize = Dimension(
                width.coerceAtLeast(120), // Minimum width
                height.coerceAtLeast(40)  // Minimum height
            )
        } else {
            preferredSize = Dimension(250, 200)
        }
    }

    private fun setupCodeSnippetView() {
        val code = node.getCodeSnippet(project)
        val newCodeArea = JBTextArea(code)
        this.codeArea = newCodeArea

        newCodeArea.isEditable = false
        newCodeArea.isEnabled = false  // Prevent selection
        newCodeArea.highlighter = null // Disable highlighting
        newCodeArea.font = Font("Monospaced", Font.PLAIN, 12)
        newCodeArea.background = NODE_BACKGROUND
        newCodeArea.foreground = NODE_TEXT_COLOR
        newCodeArea.caretColor = NODE_TEXT_COLOR
        
        // Ensure clicks on the text area propagate to the parent for dragging
        newCodeArea.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                e.source = this@NodeComponent
                this@NodeComponent.dispatchEvent(e)
            }
            
            override fun mouseReleased(e: MouseEvent) {
                e.source = this@NodeComponent
                this@NodeComponent.dispatchEvent(e)
            }
            
            override fun mouseClicked(e: MouseEvent) {
                e.source = this@NodeComponent
                this@NodeComponent.dispatchEvent(e)
            }
        })

        val scrollPane = JBScrollPane(newCodeArea)
        scrollPane.preferredSize = Dimension(200, 150)
        scrollPane.border = LineBorder(JBColor.border(), 1)
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun setupContextMenu() {
        val menu = JPopupMenu()

        val navigateItem = JMenuItem("Navigate to Bookmark")
        navigateItem.addActionListener {
            node.navigateToBookmark(project)
        }
        menu.add(navigateItem)

        val toggleSnippetItem = JMenuItem(
            if (node.showCodeSnippet) "Hide Code Snippet" else "Show Code Snippet"
        )
        toggleSnippetItem.addActionListener {
            node.showCodeSnippet = !node.showCodeSnippet

            // Remove existing components
            removeAll()
            add(titleLabel, BorderLayout.NORTH)

            // Re-add code area if needed
            if (node.showCodeSnippet) {
                setupCodeSnippetView()
            }

            // Update component size based on content
            updatePreferredSize()
            revalidate()
            repaint()

            // Save changes
            val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel
            canvas?.let {
                CanvasPersistenceService.getInstance().saveCanvasState(project, it.canvasState)
            }
        }
        menu.add(toggleSnippetItem)

        val removeItem = JMenuItem("Remove from Canvas")
        removeItem.addActionListener {
            val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel
            canvas?.let {
                it.canvasState.removeNode(node.id)
                it.remove(this)
                it.repaint()
                CanvasPersistenceService.getInstance().saveCanvasState(project, it.canvasState)
            }
        }
        menu.add(removeItem)

        // Context lines submenu
        val contextLinesMenu = JMenu("Context Lines")
        val setContextLinesItem = JMenuItem("Set Context Lines...")
        setContextLinesItem.addActionListener {
            val input = JOptionPane.showInputDialog(
                this,
                "Enter number of context lines (before,after):",
                "${node.contextLinesBefore},${node.contextLinesAfter}"
            )

            if (!input.isNullOrEmpty()) {
                try {
                    val parts = input.split(",")
                    if (parts.size == 2) {
                        node.contextLinesBefore = parts[0].trim().toInt()
                        node.contextLinesAfter = parts[1].trim().toInt()

                        if (node.showCodeSnippet) {
                            // Refresh code display
                            removeAll()
                            add(titleLabel, BorderLayout.NORTH)
                            setupCodeSnippetView()
                            revalidate()
                            repaint()
                        }

                        val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel
                        canvas?.let {
                            CanvasPersistenceService.getInstance().saveCanvasState(project, it.canvasState)
                        }
                    }
                } catch (ex: NumberFormatException) {
                    JOptionPane.showMessageDialog(this, "Invalid input format")
                }
            }
        }
        contextLinesMenu.add(setContextLinesItem)
        menu.add(contextLinesMenu)

        val createConnectionItem = JMenuItem("Create Connection From This Node")
        createConnectionItem.addActionListener {
            val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel
            canvas?.connectionStartNode = this
        }
        menu.add(createConnectionItem)

        componentPopupMenu = menu
    }

    private fun setupDragBehavior() {
        val adapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragStart = e.point
                    isDragging = true
                } else if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger) {
                    // Start connection from this node (works for right-click and Mac touchpad double-tap)
                    val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel
                    canvas?.connectionStartNode = this@NodeComponent
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                isDragging = false
                // Save node position when dropped
                val location = location
                node.position = location
                val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel
                canvas?.let {
                    CanvasPersistenceService.getInstance().saveCanvasState(project, it.canvasState)
                }
            }

            override fun mouseDragged(e: MouseEvent) {
                if (isDragging) {
                    val current = e.point
                    val location = location

                    // Calculate new position
                    val newX = location.x + (current.x - dragStart!!.x)
                    val newY = location.y + (current.y - dragStart!!.y)

                    // Set new position
                    setLocation(newX, newY)

                    // Repaint parent to update connections
                    parent?.repaint()
                }
            }
            
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2) {
                    // Navigate to bookmark on double-click
                    node.navigateToBookmark(project)
                }
            }
        }

        addMouseListener(adapter)
        addMouseMotionListener(adapter)
    }

    private fun setupKeyboardNavigation() {
        // Add keyboard navigation
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        // Navigate to bookmark
                        node.navigateToBookmark(project)
                    }
                    KeyEvent.VK_DELETE -> {
                        // Remove from canvas
                        val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel
                        canvas?.let {
                            it.canvasState.removeNode(node.id)
                            it.remove(this@NodeComponent)
                            it.repaint()
                            CanvasPersistenceService.getInstance().saveCanvasState(project, it.canvasState)
                        }
                    }
                }
            }
        })

        // Make component focusable
        isFocusable = true
    }
}