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
    private var isResizing = false

    companion object {
        private val NODE_BACKGROUND = JBColor(
            Color(250, 250, 250), // Light mode
            Color(43, 43, 43), // Dark mode
        )
        private val NODE_TEXT_COLOR = JBColor(
            Color(0, 0, 0), // Light mode
            Color(187, 187, 187), // Dark mode
        )
        private val RESIZE_HANDLE_COLOR = JBColor(
            Color(180, 180, 180), // Light mode
            Color(100, 100, 100), // Dark mode
        )
        private val TITLE_PADDING = 8
        private val CONTENT_PADDING = 10
        private val RESIZE_HANDLE_SIZE = 10
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
                // Forward to parent component
                val parentEvent = SwingUtilities.convertMouseEvent(newCodeArea, e, this@NodeComponent)
                this@NodeComponent.dispatchEvent(parentEvent)
                e.consume() // Prevent normal processing
            }
            
            override fun mouseReleased(e: MouseEvent) {
                val parentEvent = SwingUtilities.convertMouseEvent(newCodeArea, e, this@NodeComponent)
                this@NodeComponent.dispatchEvent(parentEvent)
                e.consume()
            }
            
            override fun mouseClicked(e: MouseEvent) {
                val parentEvent = SwingUtilities.convertMouseEvent(newCodeArea, e, this@NodeComponent)
                this@NodeComponent.dispatchEvent(parentEvent)
                e.consume()
            }
        })
        
        // Also handle mouse drags
        newCodeArea.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                val parentEvent = SwingUtilities.convertMouseEvent(newCodeArea, e, this@NodeComponent)
                this@NodeComponent.dispatchEvent(parentEvent)
                e.consume()
            }
            
            override fun mouseMoved(e: MouseEvent) {
                val parentEvent = SwingUtilities.convertMouseEvent(newCodeArea, e, this@NodeComponent)
                this@NodeComponent.dispatchEvent(parentEvent)
                e.consume()
            }
        })
        
        // Create scroll pane
        val scrollPane = JBScrollPane(newCodeArea)
        scrollPane.preferredSize = Dimension(200, 150)
        scrollPane.border = LineBorder(JBColor.border(), 1)
        
        // Also apply the same event forwarding to the scroll pane
        scrollPane.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val parentEvent = SwingUtilities.convertMouseEvent(scrollPane, e, this@NodeComponent)
                this@NodeComponent.dispatchEvent(parentEvent)
                e.consume()
            }
            
            override fun mouseReleased(e: MouseEvent) {
                val parentEvent = SwingUtilities.convertMouseEvent(scrollPane, e, this@NodeComponent)
                this@NodeComponent.dispatchEvent(parentEvent)
                e.consume()
            }
            
            override fun mouseClicked(e: MouseEvent) {
                val parentEvent = SwingUtilities.convertMouseEvent(scrollPane, e, this@NodeComponent)
                this@NodeComponent.dispatchEvent(parentEvent)
                e.consume()
            }
        })
        
        scrollPane.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                val parentEvent = SwingUtilities.convertMouseEvent(scrollPane, e, this@NodeComponent)
                this@NodeComponent.dispatchEvent(parentEvent)
                e.consume()
            }
        })
        
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
                    val resizeArea = isInResizeArea(e.point)
                    if (resizeArea) {
                        isResizing = true
                    } else {
                        isDragging = true
                    }
                    dragStart = e.point
                } else if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger) {
                    // Start connection from this node (works for right-click and Mac touchpad double-tap)
                    val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel
                    canvas?.connectionStartNode = this@NodeComponent
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (isDragging || isResizing) {
                    // Save position and size when released
                    val location = location
                    node.position = location
                    
                    val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel
                    canvas?.let {
                        CanvasPersistenceService.getInstance().saveCanvasState(project, it.canvasState)
                    }
                }
                isDragging = false
                isResizing = false
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
                } else if (isResizing) {
                    val current = e.point
                    val widthDelta = current.x - dragStart!!.x
                    val heightDelta = current.y - dragStart!!.y
                    
                    // Calculate new size
                    val newWidth = (width + widthDelta).coerceAtLeast(120)
                    val newHeight = (height + heightDelta).coerceAtLeast(40)
                    
                    // Update size
                    setSize(newWidth, newHeight)
                    preferredSize = Dimension(newWidth, newHeight)
                    
                    // Update drag start point
                    dragStart = current
                    
                    revalidate()
                    repaint()
                    parent?.repaint()  // Update connections
                }
            }
            
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2) {
                    // Navigate to bookmark on double-click
                    node.navigateToBookmark(project)
                }
            }
            
            override fun mouseMoved(e: MouseEvent) {
                // Change cursor when over resize area
                if (isInResizeArea(e.point)) {
                    cursor = Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
                } else {
                    cursor = Cursor.getDefaultCursor()
                }
            }
        }

        addMouseListener(adapter)
        addMouseMotionListener(adapter)
    }
    
    private fun isInResizeArea(point: Point): Boolean {
        val resizeArea = Rectangle(
            width - RESIZE_HANDLE_SIZE, 
            height - RESIZE_HANDLE_SIZE,
            RESIZE_HANDLE_SIZE,
            RESIZE_HANDLE_SIZE
        )
        return resizeArea.contains(point)
    }
    
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        
        // Draw resize handle in bottom-right corner
        val g2d = g.create() as Graphics2D
        g2d.color = RESIZE_HANDLE_COLOR
        
        // Draw diagonal lines for resize handle
        val x = width - RESIZE_HANDLE_SIZE
        val y = height - RESIZE_HANDLE_SIZE
        for (i in 1..3) {
            val offset = i * 3
            g2d.drawLine(
                x + offset, y + RESIZE_HANDLE_SIZE,
                x + RESIZE_HANDLE_SIZE, y + offset
            )
        }
        
        g2d.dispose()
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