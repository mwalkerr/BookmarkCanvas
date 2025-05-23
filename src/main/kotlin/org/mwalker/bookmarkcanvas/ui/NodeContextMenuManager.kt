package org.mwalker.bookmarkcanvas.ui

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPopupMenu

/**
 * Manages context menu creation and handling for NodeComponent
 */
class NodeContextMenuManager(
    private val nodeComponent: NodeComponent,
    private val node: BookmarkNode,
    private val project: Project,
    private val titlePanel: javax.swing.JPanel
) {
    private val LOG = Logger.getInstance(NodeContextMenuManager::class.java)
    private var menu: JPopupMenu? = null
    
    /**
     * Creates and configures the context menu
     */
    fun createContextMenu(): JPopupMenu {
        val menu = JPopupMenu()

        // Navigate option
        val navigateItem = JMenuItem("Navigate to Bookmark")
        navigateItem.addActionListener {
            node.navigateToBookmark(project)
        }
        menu.add(navigateItem)

        // Edit title option
        val editTitleItem = JMenuItem("Edit Title")
        editTitleItem.addActionListener {
            showEditTitleDialog()
        }
        menu.add(editTitleItem)

        // Toggle code snippet option
        val toggleSnippetItem = JMenuItem(
            if (node.showCodeSnippet) "Hide Code Snippet" else "Show Code Snippet"
        )
        toggleSnippetItem.addActionListener {
            toggleCodeSnippet()
            // Update the menu item text after toggle
            toggleSnippetItem.text = if (node.showCodeSnippet) "Hide Code Snippet" else "Show Code Snippet"
        }
        menu.add(toggleSnippetItem)

        // Remove option
        val removeItem = JMenuItem("Remove from Canvas")
        removeItem.addActionListener {
            removeFromCanvas()
        }
        menu.add(removeItem)

        // Context lines submenu
        val contextLinesMenu = JMenu("Context Lines")
        val setContextLinesItem = JMenuItem("Set Context Lines...")
        setContextLinesItem.addActionListener {
            showContextLinesDialog()
        }
        contextLinesMenu.add(setContextLinesItem)
        menu.add(contextLinesMenu)

        // Connection creation option
        val createConnectionItem = JMenuItem("Create Connection From This Node")
        createConnectionItem.addActionListener {
            startConnection()
        }
        menu.add(createConnectionItem)
        
        menu.addSeparator()
        
        // Z-order options
        val sendToFrontItem = JMenuItem("Send to Front")
        sendToFrontItem.addActionListener {
            sendToFront()
        }
        menu.add(sendToFrontItem)
        
        val sendToBackItem = JMenuItem("Send to Back")
        sendToBackItem.addActionListener {
            sendToBack()
        }
        menu.add(sendToBackItem)
        
        this.menu = menu
        return menu
    }
    
    /**
     * Shows the context menu at the specified coordinates
     */
    fun showContextMenu(x: Int, y: Int) {
        menu?.show(nodeComponent, x, y)
    }
    
    /**
     * @return The created popup menu
     */
    fun getMenu(): JPopupMenu? = menu
    
    /**
     * Shows a dialog to edit the node title
     */
    private fun showEditTitleDialog() {
        val input = JOptionPane.showInputDialog(
            nodeComponent,
            "Enter new title:",
            node.getDisplayText()
        )

        if (!input.isNullOrEmpty()) {
            node.displayName = input
            
            // Signal to update title text
            (nodeComponent as? NodeComponentInternal)?.updateTitle(input)
            
            // Save changes
            saveCanvasState()
        }
    }
    
    /**
     * Toggles the display of code snippet
     */
    private fun toggleCodeSnippet() {
        node.showCodeSnippet = !node.showCodeSnippet

        // Signal the node component to update its layout
        (nodeComponent as? NodeComponentInternal)?.refreshLayout()

        // Save changes
        saveCanvasState()
    }
    
    /**
     * Removes the node from canvas
     */
    private fun removeFromCanvas() {
        val canvas = nodeComponent.parent as? CanvasPanel
        canvas?.let {
            it.canvasState.removeNode(node.id)
            it.remove(nodeComponent)
            it.repaint()
            saveCanvasState()
        }
    }
    
    /**
     * Shows dialog to configure context lines
     */
    private fun showContextLinesDialog() {
        val input = JOptionPane.showInputDialog(
            nodeComponent,
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
                        // Signal to refresh the layout with new context lines
                        (nodeComponent as? NodeComponentInternal)?.refreshLayout()
                    }

                    saveCanvasState()
                }
            } catch (ex: NumberFormatException) {
                JOptionPane.showMessageDialog(nodeComponent, "Invalid input format")
            }
        }
    }
    
    /**
     * Initiates a connection from this node
     */
    private fun startConnection() {
        val canvas = nodeComponent.parent as? CanvasPanel
        canvas?.connectionStartNode = nodeComponent
    }
    
    /**
     * Sends the node to the front (top z-order)
     */
    private fun sendToFront() {
        val canvas = nodeComponent.parent as? CanvasPanel
        canvas?.let {
            // Remove and re-add at index 0 to bring to front
            it.remove(nodeComponent)
            it.add(nodeComponent, 0)
            it.revalidate()
            it.repaint()
        }
    }
    
    /**
     * Sends the node to the back (bottom z-order)
     */
    private fun sendToBack() {
        val canvas = nodeComponent.parent as? CanvasPanel
        canvas?.let {
            // Remove and re-add at the last position to send to back
            it.remove(nodeComponent)
            it.add(nodeComponent)
            it.revalidate()
            it.repaint()
        }
    }
    
    /**
     * Saves the current canvas state
     */
    private fun saveCanvasState() {
        val canvas = nodeComponent.parent as? CanvasPanel
        canvas?.let {
            CanvasPersistenceService.getInstance().saveCanvasState(project, it.canvasState)
        }
    }
    
    /**
     * Interface for internal NodeComponent operations 
     * needed by the context menu manager
     */
    interface NodeComponentInternal {
        fun updateTitle(title: String)
        fun refreshLayout()
    }
}