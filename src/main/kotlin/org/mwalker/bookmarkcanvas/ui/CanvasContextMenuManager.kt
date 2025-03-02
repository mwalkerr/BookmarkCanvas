package org.mwalker.bookmarkcanvas.ui

import com.intellij.openapi.project.Project
import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.services.BookmarkService
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Point
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.DefaultListCellRenderer

/**
 * Manages context menus on the canvas
 */
class CanvasContextMenuManager(
    private val canvasPanel: CanvasPanel,
    private val project: Project
) {
    /**
     * Shows the canvas context menu at the specified point
     */
    fun showCanvasContextMenu(point: Point) {
        val menu = JPopupMenu()
        
        val addBookmarkItem = JMenuItem("Add Bookmark")
        addBookmarkItem.addActionListener {
            showAddBookmarkDialog(point)
        }
        menu.add(addBookmarkItem)
        
        val addAllBookmarksItem = JMenuItem("Add All Bookmarks")
        addAllBookmarksItem.addActionListener {
            refreshBookmarks()
        }
        menu.add(addAllBookmarksItem)

        val clearCanvasItem = JMenuItem("Clear Canvas")
        clearCanvasItem.addActionListener {
            canvasPanel.clearCanvas()
        }
        menu.add(clearCanvasItem)

        menu.show(canvasPanel, point.x, point.y)
    }
    
    /**
     * Shows dialog to select and add a bookmark
     */
    fun showAddBookmarkDialog(point: Point) {
        // Get all available bookmarks
        val allBookmarks = BookmarkService.getAllBookmarkNodes(project)
        
        if (allBookmarks.isEmpty()) {
            JOptionPane.showMessageDialog(
                canvasPanel,
                "No bookmarks found. Create bookmarks in your editor first.",
                "No Bookmarks",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        
        // Create dialog for selecting a bookmark
        val dialog = JDialog()
        dialog.title = "Select Bookmark"
        dialog.layout = BorderLayout()
        dialog.isModal = true
        
        val bookmarkList = JList(allBookmarks.toTypedArray())
        bookmarkList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val bookmark = value as BookmarkNode
                label.text = bookmark.displayName
                return label
            }
        }
        
        val scrollPane = JScrollPane(bookmarkList)
        dialog.add(scrollPane, BorderLayout.CENTER)
        
        val buttonPanel = JPanel()
        buttonPanel.layout = FlowLayout(FlowLayout.RIGHT)
        val addButton = JButton("Add")
        addButton.addActionListener {
            val selectedBookmark = bookmarkList.selectedValue
            if (selectedBookmark != null) {
                // Create a copy of the bookmark with new position
                val nodeCopy = selectedBookmark.copy(
                    id = "bookmark_" + System.currentTimeMillis(),
                    positionX = (point.x / canvasPanel.zoomFactor).toInt(),
                    positionY = (point.y / canvasPanel.zoomFactor).toInt()
                )
                
                canvasPanel.canvasState.addNode(nodeCopy)
                canvasPanel.addNodeComponent(nodeCopy)
                CanvasPersistenceService.getInstance().saveCanvasState(project, canvasPanel.canvasState)
                canvasPanel.repaint()
            }
            dialog.dispose()
        }
        
        val cancelButton = JButton("Cancel")
        cancelButton.addActionListener { dialog.dispose() }
        
        buttonPanel.add(addButton)
        buttonPanel.add(cancelButton)
        dialog.add(buttonPanel, BorderLayout.SOUTH)
        
        dialog.pack()
        dialog.setSize(400, 300)
        dialog.setLocationRelativeTo(canvasPanel)
        dialog.isVisible = true
    }
    
    /**
     * Adds all bookmarks to the canvas
     */
    fun refreshBookmarks() {
        // Fetch all bookmarks from the IDE
        val bookmarks = BookmarkService.getAllBookmarkNodes(project)
        
        // For each bookmark not already on canvas, add a new node
        var added = 0
        for (bookmark in bookmarks) {
            // Check if this bookmark is already on the canvas
            val existing = canvasPanel.canvasState.nodes.values.find { 
                it.bookmarkId == bookmark.bookmarkId 
            }
            
            if (existing == null) {
                // Find free position for new node
                val offset = canvasPanel.nodeComponents.size * 30
                val newNode = bookmark.copy(
                    id = "bookmark_" + System.currentTimeMillis(),
                    positionX = 100 + offset,
                    positionY = 100 + offset
                )
                
                canvasPanel.canvasState.addNode(newNode)
                canvasPanel.addNodeComponent(newNode)
                added++
            }
        }
        
        if (added > 0) {
            CanvasPersistenceService.getInstance().saveCanvasState(project, canvasPanel.canvasState)
            canvasPanel.repaint()
        }
    }
}