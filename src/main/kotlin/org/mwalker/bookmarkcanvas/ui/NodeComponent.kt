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
    var isSelected = false
        set(value) {
            if (field != value) {
                field = value
                repaint()
            }
        }
    private var twoFingerTapStartPoint: Point? = null
    private var connectionStarted = false

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
        private val SELECTION_BORDER_COLOR = JBColor(
            Color(0, 120, 215), // Light mode
            Color(75, 110, 175) // Dark mode
        )
        private val SELECTION_HEADER_COLOR = JBColor(
            Color(210, 230, 255), // Light mode
            Color(45, 65, 100) // Dark mode
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
            private fun forwardEvent(e: MouseEvent) {
                val parentEvent = SwingUtilities.convertMouseEvent(newCodeArea, e, this@NodeComponent)
                this@NodeComponent.dispatchEvent(parentEvent)
                e.consume()
            }

            override fun mousePressed(e: MouseEvent) = forwardEvent(e)
            override fun mouseReleased(e: MouseEvent) = forwardEvent(e)
            override fun mouseClicked(e: MouseEvent) = forwardEvent(e)
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
                preferredSize = Dimension(250, 200)
            } else {
                updatePreferredSize()  // Resize to fit title only
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
                // This will get the parent CanvasPanel
                val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel
                
                if (SwingUtilities.isLeftMouseButton(e)) {
                    val resizeArea = isInResizeArea(e.point)
                    if (resizeArea) {
                        isResizing = true
                        dragStart = e.point
                        e.consume() // Consume the event so it doesn't propagate
                    } else if (canvas?.selectedNodes?.contains(this@NodeComponent) == true) {
                        // If we're part of a selection group, let the canvas handle dragging
                        // Don't set isDragging here
                    } else {
                        // Individual dragging
                        isDragging = true
                        dragStart = e.point
                    }
                } else if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger) {
                    // Two-finger tap handling
                    twoFingerTapStartPoint = e.point
                    connectionStarted = false
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel
                
                if (isDragging || isResizing) {
                    // Save position and size when released
                    val location = location
                    node.position = Point(
                        (location.x / (canvas?.zoomFactor ?: 1.0)).toInt(),
                        (location.y / (canvas?.zoomFactor ?: 1.0)).toInt()
                    )
                    
                    canvas?.let {
                        CanvasPersistenceService.getInstance().saveCanvasState(project, it.canvasState)
                    }
                    isDragging = false
                    isResizing = false
                } else if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger) {
                    // Only show context menu on release if we haven't started a connection
                    if (!connectionStarted) {
                        componentPopupMenu?.show(this@NodeComponent, e.x, e.y)
                    }
                    twoFingerTapStartPoint = null
                    connectionStarted = false
                }
                
                dragStart = null
            }

            override fun mouseDragged(e: MouseEvent) {
                if (isDragging) {
                    // Get canvas to check selection
                    val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel

                    // Only handle dragging here if this node is not part of a multi-node selection
                    if (canvas != null && (!canvas.selectedNodes.contains(this@NodeComponent) ||
                                          canvas.selectedNodes.size == 1)) {
                        val current = e.point
                        val location = location

                        // Calculate new position
                        var newX = location.x + (current.x - dragStart!!.x)
                        var newY = location.y + (current.y - dragStart!!.y)

                        // Apply snap-to-grid if enabled
                        if (canvas.snapToGrid) {
                            val gridSize = (canvas.GRID_SIZE * canvas.zoomFactor).toInt()
                            newX = (newX / gridSize) * gridSize
                            newY = (newY / gridSize) * gridSize
                        }

                        // Set new position
                        setLocation(newX, newY)

                        // Repaint parent to update connections
                        canvas.repaint()
                    }
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
                } else if (twoFingerTapStartPoint != null && 
                         (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger)) {
                    // Two-finger drag to create connection
                    if (Math.abs(e.point.x - twoFingerTapStartPoint!!.x) > 5 || 
                        Math.abs(e.point.y - twoFingerTapStartPoint!!.y) > 5) {
                        // We've moved enough to consider this a connection drag
                        connectionStarted = true
                        
                        val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel
                        if (canvas?.connectionStartNode != this@NodeComponent) {
                            canvas?.connectionStartNode = this@NodeComponent
                        }
                        
                        val canvasPoint = SwingUtilities.convertPoint(
                            this@NodeComponent, e.point, canvas
                        )
                        canvas?.tempConnectionEndPoint = canvasPoint
                        canvas?.repaint()
                    }
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
        val g2d = g.create() as Graphics2D
        
        // Set rendering hints for smoother lines
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        // Draw selection border and header if selected
        if (isSelected) {
            // Draw title background highlight
            val headerRect = Rectangle(0, 0, width, titleLabel.height + TITLE_PADDING * 2)
            g2d.color = SELECTION_HEADER_COLOR
            g2d.fill(headerRect)
            
            // Draw selection border
            g2d.color = SELECTION_BORDER_COLOR
            g2d.stroke = BasicStroke(2.0f)
            g2d.drawRect(1, 1, width - 3, height - 3)
        }
        
        // Draw resize handle in bottom-right corner
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