package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.services.BookmarkService
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import org.mwalker.bookmarkcanvas.ui.CanvasPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.annotations.NotNull
import com.intellij.openapi.diagnostic.Logger
import org.mwalker.bookmarkcanvas.ui.CanvasToolbar
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile

class FindOnCanvasAction : AnAction() {
    companion object {
        private val LOG = Logger.getInstance(FindOnCanvasAction::class.java)
    }
    
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        
        val currentLine = editor.caretModel.logicalPosition.line
        val filePath = getRelativeFilePath(project, virtualFile)
        
        LOG.info("FindOnCanvasAction: Looking for nodes containing $filePath:$currentLine")
        
        // Get the canvas state and find matching nodes
        val canvasState = CanvasPersistenceService.getInstance().getCanvasState(project)
        val matchingNodes = findNodesContainingLine(canvasState.nodes.values, filePath, currentLine)
        
        if (matchingNodes.isEmpty()) {
            LOG.info("No nodes found containing the current line")
            return
        }
        
        LOG.info("Found ${matchingNodes.size} nodes containing the current line")
        
        // Make sure the tool window is visible
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("BookmarkCanvas")
        if (toolWindow != null && !toolWindow.isVisible) {
            toolWindow.show(null)
        }
        
        // Center and select the matching nodes
        if (toolWindow != null) {
            val canvasPanel = findCanvasPanel(toolWindow)
            if (canvasPanel != null) {
                centerAndSelectNodes(canvasPanel, matchingNodes)
            }
        }
    }
    
    /**
     * Find nodes that contain the specified line within their span
     */
    private fun findNodesContainingLine(nodes: Collection<BookmarkNode>, filePath: String, currentLine: Int): List<BookmarkNode> {
        return nodes.filter { node ->
            // Check if the file path matches (handle both absolute and relative paths)
            val nodeFilePath = getNodeFilePath(node)
            val isFileMatch = nodeFilePath == filePath || 
                             nodeFilePath.endsWith("/$filePath") || 
                             filePath.endsWith("/$nodeFilePath")
            
            if (!isFileMatch) return@filter false
            
            // Check if the current line is within the node's span
            val nodeStartLine = node.lineNumber0Based
            val nodeEndLine = if (node.showCodeSnippet && node.contextLinesAfter > 0) {
                nodeStartLine + node.contextLinesAfter
            } else {
                nodeStartLine
            }
            
            currentLine >= nodeStartLine && currentLine <= nodeEndLine
        }
    }
    
    /**
     * Get the file path for comparison, handling both absolute and relative paths
     */
    private fun getNodeFilePath(node: BookmarkNode): String {
        return if (node.filePath.startsWith("/") || node.filePath.contains(":\\")) {
            // Absolute path - extract the relative part
            node.filePath.substringAfterLast("/")
        } else {
            node.filePath
        }
    }
    
    /**
     * Get relative file path from project root
     */
    private fun getRelativeFilePath(project: Project, virtualFile: VirtualFile): String {
        val projectBasePath = project.basePath ?: return virtualFile.path
        return if (virtualFile.path.startsWith(projectBasePath)) {
            virtualFile.path.substring(projectBasePath.length + 1)
        } else {
            virtualFile.path
        }
    }
    
    /**
     * Center the canvas view on the matching nodes and select them
     */
    private fun centerAndSelectNodes(canvasPanel: CanvasPanel, nodes: List<BookmarkNode>) {
        // Clear current selection
        canvasPanel.selectionManager.clearSelection()
        
        // Find the node components for the matching nodes
        val nodeComponents = nodes.mapNotNull { node ->
            canvasPanel.nodeComponents[node.id]
        }
        
        if (nodeComponents.isEmpty()) return
        
        // Add all matching nodes to selection
        for (nodeComponent in nodeComponents) {
            canvasPanel.selectedNodes.add(nodeComponent)
            nodeComponent.isSelected = true
            nodeComponent.repaint()
        }
        
        // Find the extreme points of all nodes in logical coordinates
        val extremePoints = findExtremePoints(nodeComponents)
        
        // Get the canvas size
        val canvasWidth = canvasPanel.width
        val canvasHeight = canvasPanel.height
        
        // Calculate the logical bounds with padding
        val padding = 50 // Padding in logical coordinates
        val logicalWidth = extremePoints.maxX - extremePoints.minX + padding * 2
        val logicalHeight = extremePoints.maxY - extremePoints.minY + padding * 2
        
        // Calculate zoom to fit all nodes with padding
        val zoomX = canvasWidth.toDouble() / logicalWidth
        val zoomY = canvasHeight.toDouble() / logicalHeight
        
        // Apply different max zoom limits based on number of nodes
        val maxZoom = if (nodeComponents.size == 1) {
            0.5 // More conservative zoom for single nodes to avoid over-zooming
        } else {
            0.5 // Standard zoom limit for multiple nodes
        }
        
        val targetZoom = minOf(zoomX, zoomY, maxZoom)
        
        // Set the zoom factor
        canvasPanel._zoomFactor = targetZoom
        canvasPanel.canvasState.zoomFactor = targetZoom
        
        // Calculate the center point of the logical bounds
        val logicalCenterX = extremePoints.minX + (extremePoints.maxX - extremePoints.minX) / 2 - padding
        val logicalCenterY = extremePoints.minY + (extremePoints.maxY - extremePoints.minY) / 2 - padding
        
        // Calculate how much to offset all nodes to center the view
        val targetLogicalCenterX = canvasWidth / (2 * targetZoom)
        val targetLogicalCenterY = canvasHeight / (2 * targetZoom)
        
        val offsetX = (targetLogicalCenterX - logicalCenterX).toInt()
        val offsetY = (targetLogicalCenterY - logicalCenterY).toInt()
        
        // Apply offset to all nodes to center the view
        for (nodeComp in canvasPanel.nodeComponents.values) {
            val currentPos = nodeComp.node.position
            nodeComp.node.position = java.awt.Point(
                currentPos.x + offsetX,
                currentPos.y + offsetY
            )
        }
        
        // Update canvas and save state
        canvasPanel.zoomManager.updateCanvasSize()
        canvasPanel.revalidate()
        canvasPanel.repaint()
        
        // Save the updated state
        CanvasPersistenceService.getInstance().saveCanvasState(canvasPanel.project, canvasPanel.canvasState)
        
        LOG.info("Centered and selected ${nodeComponents.size} nodes with zoom ${targetZoom}")
    }
    
    /**
     * Data class to hold extreme points of nodes
     */
    private data class ExtremePoints(
        val minX: Int,
        val minY: Int, 
        val maxX: Int,
        val maxY: Int
    )
    
    /**
     * Find the extreme points (corners) of all the given node components in logical coordinates
     */
    private fun findExtremePoints(nodeComponents: List<org.mwalker.bookmarkcanvas.ui.NodeComponent>): ExtremePoints {
        if (nodeComponents.isEmpty()) return ExtremePoints(0, 0, 0, 0)
        
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        
        for (nodeComp in nodeComponents) {
            // Use logical position (node.position) not screen position (nodeComp.x/y)
            val logicalX = nodeComp.node.position.x
            val logicalY = nodeComp.node.position.y
            val logicalWidth = if (nodeComp.node.width > 0) nodeComp.node.width else nodeComp.preferredSize.width
            val logicalHeight = if (nodeComp.node.height > 0) nodeComp.node.height else nodeComp.preferredSize.height
            
            // Find extreme points
            minX = minOf(minX, logicalX)
            minY = minOf(minY, logicalY)
            maxX = maxOf(maxX, logicalX + logicalWidth)
            maxY = maxOf(maxY, logicalY + logicalHeight)
        }
        
        return ExtremePoints(minX, minY, maxX, maxY)
    }
    
    /**
     * Find the canvas panel from the tool window
     */
    private fun findCanvasPanel(toolWindow: ToolWindow): CanvasPanel? {
        val component = toolWindow.contentManager.getContent(0)?.component
        return (component as? CanvasToolbar)?.canvasPanel
    }
    
    override fun update(@NotNull e: AnActionEvent) {
        // Only enable the action when we have an open editor and the bookmark canvas tool window exists
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        val isEnabled = project != null && 
                       editor != null && 
                       virtualFile != null &&
                       hasNodesInCanvas(project)
        
        e.presentation.isEnabledAndVisible = isEnabled
    }
    
    /**
     * Check if there are any nodes in the canvas for the current project
     */
    private fun hasNodesInCanvas(project: Project?): Boolean {
        if (project == null) return false
        val canvasState = CanvasPersistenceService.getInstance().getCanvasState(project)
        return canvasState.nodes.isNotEmpty()
    }
}