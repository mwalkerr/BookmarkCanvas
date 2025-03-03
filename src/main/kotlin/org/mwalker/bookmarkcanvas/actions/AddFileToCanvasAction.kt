package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import org.mwalker.bookmarkcanvas.ui.CanvasPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.annotations.NotNull
import org.mwalker.bookmarkcanvas.ui.CanvasToolbar
import java.util.UUID

class AddFileToCanvasAction : AnAction() {
    companion object {
        private val LOG = Logger.getInstance(AddFileToCanvasAction::class.java)
    }

    override fun actionPerformed(@NotNull e: AnActionEvent) {
        LOG.info("AddFileToCanvasAction.actionPerformed called")
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        // Create a node for the file with line 0
        val node = createNodeFromFile(project, file)

        // Get or create the canvas state for this project
        val canvasState = CanvasPersistenceService.getInstance().getCanvasState(project)

        // Add the node
        canvasState.addNode(node)

        // Save state
        CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)

        // Make sure the tool window is visible
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("BookmarkCanvas")
        if (toolWindow != null && !toolWindow.isVisible) {
            toolWindow.show(null)
        }

        // Refresh the UI if the tool window is open
        if (toolWindow != null) {
            // Find the canvas panel and trigger a refresh
            val canvasPanel = findCanvasPanel(toolWindow)
            if (canvasPanel != null) {
                canvasPanel.addNodeComponent(node)
                canvasPanel.revalidate()
                canvasPanel.repaint()
            }
        }
    }

    private fun createNodeFromFile(project: Project, file: VirtualFile): BookmarkNode {
        // Create relative path from project base
        val filePath = file.path.replace(project.basePath + "/", "")

        return BookmarkNode(
            bookmarkId = "file_bookmark_" + UUID.randomUUID().toString(),
            filePath = filePath,
            lineNumber0Based = 0,
            lineContent = null
        )
    }

    private fun findCanvasPanel(toolWindow: ToolWindow): CanvasPanel? {
        val component = toolWindow.contentManager.getContent(0)?.component
        return (component as? CanvasToolbar)?.canvasPanel
    }

    override fun update(@NotNull e: AnActionEvent) {
        // Enable the action when we have a file selected
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = (
                project != null && file != null && !file.isDirectory
        )
    }
}