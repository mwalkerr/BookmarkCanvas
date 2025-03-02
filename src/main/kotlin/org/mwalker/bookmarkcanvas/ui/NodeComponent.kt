package org.mwalker.bookmarkcanvas.ui


import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import com.intellij.openapi.diagnostic.Logger
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
import javax.swing.text.JTextComponent
import javax.swing.text.View
import kotlin.math.abs

class NodeComponent(val node: BookmarkNode, private val project: Project) : JPanel() {
    private val titleLabel: JBLabel // Kept for compatibility with existing code
    private lateinit var titleTextPane: JTextPane // For displaying wrapped title
    private lateinit var titlePanel: JPanel // Panel containing the title
    private var codeArea: JBTextArea? = null
    private var dragStart: Point? = null
    private var isDragging = false
    private var isResizing = false
    private var menu: JPopupMenu? = null
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
        private val LOG = Logger.getInstance(NodeComponent::class.java)
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
        private const val BASE_TITLE_FONT_SIZE = 12
        private const val BASE_CODE_FONT_SIZE = 12
    }

    init {
        layout = BorderLayout()
        border = CompoundBorder(
            LineBorder(JBColor.border(), 1, true),
            EmptyBorder(TITLE_PADDING, CONTENT_PADDING, TITLE_PADDING, CONTENT_PADDING)
        )
        background = NODE_BACKGROUND

        // Title area - use JTextPane for better text wrapping
        titleTextPane = object : JTextPane() {
            override fun contains(x: Int, y: Int): Boolean {
                return false
            }
        }.apply {
            text = node.displayName
            foreground = NODE_TEXT_COLOR
            document.putProperty("ForegroundColor", NODE_TEXT_COLOR)  // Ensure StyledDocument uses correct color
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
        
        // Store reference as titleLabel for compatibility with existing code
        titleLabel = object : JBLabel() {
            override fun contains(x: Int, y: Int): Boolean {
                return false
            }
        }.apply {
            isVisible = false // Not actually used for display
        }
        
        add(titlePanel, BorderLayout.NORTH)

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
        LOG.info("Updating size for node: ${node.displayName}, showCodeSnippet: ${node.showCodeSnippet}")
        if (!node.showCodeSnippet) {
            // Calculate text height using LineBreakMeasurer for accurate wrapping
            val displayText = node.displayName
            val maxWidth = 250 - (CONTENT_PADDING * 2)
            
            // Calculate text height with line break measurer
            val textHeight = calculateTextHeight(displayText, titleTextPane.font, maxWidth)
            
            // Add padding to text height
            val totalHeight = textHeight + (TITLE_PADDING * 2) + 10
            
            // Set size directly for title pane
            val titleSize = Dimension(maxWidth, textHeight.coerceAtLeast(20))
            titleTextPane.setSize(titleSize)
            titleTextPane.preferredSize = titleSize

            // Use both setSize and preferredSize for better compatibility
            val newSize = Dimension(
                maxWidth + (CONTENT_PADDING * 2), // Width with padding
                totalHeight.coerceAtLeast(40)     // Minimum height
            )

            setSize(newSize)
            preferredSize = newSize
        } else {
            val newSize = Dimension(250, 200)
            setSize(newSize)
            preferredSize = newSize
        }
        LOG.info("New preferred size: $preferredSize")
    }

    /**
     * Calculates text height using LineBreakMeasurer for accurate line wrapping
     */
    private fun calculateTextHeight(text: String, font: Font, width: Int): Int {
        LOG.info("Calculating text height for: $text, width: $width")
        if (text.isEmpty()) return 20
        
        val frc = getFontRenderContext()
        val attributedString = java.text.AttributedString(text)
        attributedString.addAttribute(java.awt.font.TextAttribute.FONT, font)
        
        val iterator = attributedString.iterator
        val measurer = java.awt.font.LineBreakMeasurer(iterator, frc)
        
        var y = 0
        measurer.position = 0
        // Calculate height by measuring each line
        while (measurer.position < text.length) {
            val layout = measurer.nextLayout(width.toFloat())
            y += ((layout.ascent + layout.descent + layout.leading) * 2).toInt()
        }
        
        return y.coerceAtLeast(20) // Minimum height of 20 pixels
    }
    
    private fun getFontRenderContext(): java.awt.font.FontRenderContext {
        return (getGraphics() as? Graphics2D)?.fontRenderContext 
            ?: java.awt.font.FontRenderContext(null, true, true)
    }
    
    /**
     * Updates font sizes based on the current zoom factor
     */
    fun updateFontSizes(zoomFactor: Double) {
        // Update title font
        val scaledTitleSize = (BASE_TITLE_FONT_SIZE * zoomFactor).toInt().coerceAtLeast(8)
        titleTextPane.font = titleTextPane.font.deriveFont(Font.BOLD, scaledTitleSize.toFloat())
        
        // Ensure foreground color is set (fixes visibility issues)
        titleTextPane.foreground = NODE_TEXT_COLOR
        titleTextPane.document.putProperty("ForegroundColor", NODE_TEXT_COLOR)
        
        // Update code area font if present
        codeArea?.let { area ->
            val scaledCodeSize = (BASE_CODE_FONT_SIZE * zoomFactor).toInt().coerceAtLeast(8)
            area.font = Font("Monospaced", Font.PLAIN, scaledCodeSize)
        }
        
        revalidate()
        repaint()
    }

    private fun setupCodeSnippetView() {
        val code = node.getCodeSnippet(project)
        val newCodeArea = JBTextArea(code)
        this.codeArea = newCodeArea

        newCodeArea.isEditable = false
        newCodeArea.isEnabled = false  // Prevent selection
        newCodeArea.highlighter = null // Disable highlighting
        // Base code font size will be scaled by zoom factor in updateFontSizes()
        newCodeArea.font = Font("Monospaced", Font.PLAIN, 12)
        newCodeArea.background = NODE_BACKGROUND
        newCodeArea.foreground = NODE_TEXT_COLOR
        newCodeArea.caretColor = NODE_TEXT_COLOR

        // Ensure clicks on the text area propagate to the parent for dragging
        newCodeArea.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = forwardMouseEvent(newCodeArea, e, this@NodeComponent)
            override fun mouseReleased(e: MouseEvent) = forwardMouseEvent(newCodeArea, e, this@NodeComponent)
            override fun mouseClicked(e: MouseEvent) = forwardMouseEvent(newCodeArea, e, this@NodeComponent)
        })
        // Also handle mouse drags
        newCodeArea.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) = forwardMouseEvent(newCodeArea, e, this@NodeComponent)
            override fun mouseMoved(e: MouseEvent) = forwardMouseEvent(newCodeArea, e, this@NodeComponent)
        })
        
        // Create scroll pane
        val scrollPane = JBScrollPane(newCodeArea)
        scrollPane.setSize(200, 150)
        scrollPane.preferredSize = Dimension(200, 150) // Keep for layout compatibility
        scrollPane.border = LineBorder(JBColor.border(), 1)
        
        // Also apply the same event forwarding to the scroll pane
        scrollPane.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = forwardMouseEvent(scrollPane, e, this@NodeComponent)
            override fun mouseReleased(e: MouseEvent) = forwardMouseEvent(scrollPane, e, this@NodeComponent)
            override fun mouseClicked(e: MouseEvent) = forwardMouseEvent(scrollPane, e, this@NodeComponent)
        })
        
        scrollPane.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) = forwardMouseEvent(scrollPane, e, this@NodeComponent)
            override fun mouseMoved(e: MouseEvent) = forwardMouseEvent(scrollPane, e, this@NodeComponent)
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

        val editTitleItem = JMenuItem("Edit Title")
        editTitleItem.addActionListener {
            showEditTitleDialog()
        }
        menu.add(editTitleItem)

        val toggleSnippetItem = JMenuItem(
            if (node.showCodeSnippet) "Hide Code Snippet" else "Show Code Snippet"
        )
        toggleSnippetItem.addActionListener {
            node.showCodeSnippet = !node.showCodeSnippet

            // Remove existing components
            removeAll()
            add(titlePanel, BorderLayout.NORTH)

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
                            add(titlePanel, BorderLayout.NORTH)
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
        this.menu = menu
        
        // Don't set componentPopupMenu as we're handling it manually to support multi-selection
    }

    private fun setupDragBehavior() {
        val adapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                LOG.info("Mouse pressed on node: ${node.displayName}, ${e.point}, ${e.button}, clickCount: ${e.clickCount} "+
                "isPopupTrigger: ${e.isPopupTrigger}, isLeft: ${SwingUtilities.isLeftMouseButton(e)}, isRight: ${SwingUtilities.isRightMouseButton(e)}")
                // This will get the parent CanvasPanel
                val canvas = parent as org.mwalker.bookmarkcanvas.ui.CanvasPanel
                
                if (SwingUtilities.isLeftMouseButton(e)) {
                    val resizeArea = isInResizeArea(e.point)
                    if (resizeArea) {
                        isResizing = true
                        dragStart = e.point
                        e.consume() // Consume the event so it doesn't propagate
                    } else if (canvas.selectedNodes.contains(this@NodeComponent)) {
                        // If we're part of a selection group, forward the event to the canvas
                        forwardMouseEvent(this@NodeComponent, e, canvas)
                    } else {
                        // Individual dragging
                        isDragging = true
                        dragStart = e.point
                    }
                } else if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger) {
                    // Check if this node is part of a multi-selection
                    if (canvas.selectedNodes.contains(this@NodeComponent) && canvas.selectedNodes.size > 1) {
                        // Forward to canvas for group context menu
                        forwardMouseEvent(this@NodeComponent, e, canvas)
                        e.consume()
                    } else {
                        // Two-finger tap handling for single node
                        twoFingerTapStartPoint = e.point
                        connectionStarted = false
                    }
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                LOG.info("Mouse released on node: ${node.displayName}, ${e.point}, ${e.button}, clickCount: ${e.clickCount} "+
                        "isPopupTrigger: ${e.isPopupTrigger}, isLeft: ${SwingUtilities.isLeftMouseButton(e)}, isRight: ${SwingUtilities.isRightMouseButton(e)}")
                val canvas = parent as org.mwalker.bookmarkcanvas.ui.CanvasPanel
                
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
                } else if (canvas.selectedNodes.contains(this@NodeComponent)) {
                    // If we're part of a selection group, forward the event to the canvas
                    forwardMouseEvent(this@NodeComponent, e, canvas)
                } else if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger) {
                    // Check if this node is part of a multi-selection
                    if (canvas.selectedNodes.contains(this@NodeComponent) && canvas.selectedNodes.size > 1) {
                        // Forward to canvas for group context menu
                        forwardMouseEvent(this@NodeComponent, e, canvas)
                    } else if (!connectionStarted) {
                        // Only show context menu on release if we haven't started a connection
                        LOG.info("Showing context menu")
                        this@NodeComponent.menu?.show(this@NodeComponent, e.x, e.y)
                    } else {
                        // Forward connection completion event to canvas
                        forwardMouseEvent(this@NodeComponent, e, canvas)
                    }
                    twoFingerTapStartPoint = null
                    connectionStarted = false
                }
                
                dragStart = null
            }

            override fun mouseDragged(e: MouseEvent) {
                val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel
                
                // Check if we're part of a multi-selection
                if (canvas != null && canvas.selectedNodes.contains(this@NodeComponent) && canvas.selectedNodes.size > 1) {
                    // Forward the event to the canvas for group dragging
                    forwardMouseEvent(this@NodeComponent, e, canvas)
                } else if (isDragging) {
                    // Handle individual dragging
                    if (canvas != null) {
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
                    
                    // Update size - use both for better compatibility
                    val newSize = Dimension(newWidth, newHeight)
                    setSize(newSize)
                    preferredSize = newSize
                    
                    // Update drag start point
                    dragStart = current
                    
                    revalidate()
                    repaint()
                    parent?.repaint()  // Update connections
                } else if (twoFingerTapStartPoint != null && 
                         (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger)) {
                    // Two-finger drag to create connection
                    if (abs(e.point.x - twoFingerTapStartPoint!!.x) > 5 || 
                        abs(e.point.y - twoFingerTapStartPoint!!.y) > 5) {
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
                LOG.info("Mouse clicked on node: ${node.displayName}, ${e.point}, ${e.button}, clickCount: ${e.clickCount} "+
                        "isPopupTrigger: ${e.isPopupTrigger}, isLeft: ${SwingUtilities.isLeftMouseButton(e)}, isRight: ${SwingUtilities.isRightMouseButton(e)}")

                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2) {
                    // Navigate to bookmark on double-click anywhere in the node
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
    
    /**
     * Shows a dialog to edit the node title
     */
    private fun showEditTitleDialog() {
        val input = JOptionPane.showInputDialog(
            this,
            "Enter new title:",
            node.displayName
        )

        if (!input.isNullOrEmpty()) {
            node.displayName = input
            
            // Update text and ensure proper styling
            titleTextPane.text = input
            titleTextPane.foreground = NODE_TEXT_COLOR
            titleTextPane.document.putProperty("ForegroundColor", NODE_TEXT_COLOR)
            
            // Recalculate size based on new text
            updatePreferredSize()
            
            // Update UI
            titlePanel.revalidate()
            revalidate()
            repaint()
            
            // Save changes
            val canvas = parent as? org.mwalker.bookmarkcanvas.ui.CanvasPanel
            canvas?.let {
                CanvasPersistenceService.getInstance().saveCanvasState(project, it.canvasState)
            }
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