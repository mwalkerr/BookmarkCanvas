package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.model.CanvasState
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
import javax.swing.JComponent
import javax.swing.JScrollPane
import com.intellij.openapi.diagnostic.Logger
import org.mwalker.bookmarkcanvas.ui.CanvasToolbar


class AddToCanvasAction : AnAction() {
    companion object {
        private val LOG = Logger.getInstance(AddToCanvasAction::class.java)
    }
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        LOG.info("AddToCanvasAction.actionPerformed called, project: ${e.project}")
        val project = e.project ?: return
        LOG.info("AddToCanvasAction.actionPerformed called, project: ${e.project}, node: ${BookmarkService.createNodeFromCurrentPosition(e.project!!)}")

        // Create a node from the current editor position
        val node = BookmarkService.createNodeFromCurrentPosition(project) ?: return

        // Get or create the canvas state for this project
        val canvasState = CanvasPersistenceService.getInstance().getCanvasState(project)

        // Add the node
        canvasState.addNode(node)

        // Save state
        CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)


        // Make sure the tool window is visible
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("BookmarkCanvas")
        LOG.info("AddToCanvasAction.actionPerformed called, toolWindow: $toolWindow, isVisible: ${toolWindow?.isVisible}")
        if (toolWindow != null && !toolWindow.isVisible) {
            toolWindow.show(null)
        }

        LOG.info("AddToCanvasAction.actionPerformed called, toolWindow: $toolWindow, isActive: ${toolWindow?.isActive}")
        // Refresh the UI if the tool window is open
//        if (toolWindow != null && toolWindow.isActive) {
        if (toolWindow != null) {
            // Find the canvas panel and trigger a refresh
            val canvasPanel = findCanvasPanel(toolWindow)
            LOG.info("AddToCanvasAction.actionPerformed called, canvasPanel: $canvasPanel")
            if (canvasPanel != null) {
                canvasPanel.addNodeComponent(node)
                canvasPanel.revalidate()
                canvasPanel.repaint()
            }
        }
    }

    private fun findCanvasPanel(toolWindow: ToolWindow): CanvasPanel? {
        val component = toolWindow.contentManager.getContent(0)?.component
        return (component as? CanvasToolbar)?.canvasPanel
    }

    override fun update(@NotNull e: AnActionEvent) {
        // Only enable the action when we have an open editor
        val project = e.project
        e.presentation.isEnabledAndVisible = (
                project != null &&
                        e.getData(CommonDataKeys.EDITOR) != null
                )
    }
}