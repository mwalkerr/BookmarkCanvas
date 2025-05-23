package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.model.CanvasState
import org.mwalker.bookmarkcanvas.services.BookmarkService
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import org.mwalker.bookmarkcanvas.ui.CanvasInterface
import org.mwalker.bookmarkcanvas.ui.WebCanvasPanel
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
import org.mwalker.bookmarkcanvas.ui.WebCanvasToolbar

class AddToCanvasWebAction(
    private val project: Project,
    private val canvasPanel: CanvasInterface
) : AnAction("Add to Canvas", "Add current position to canvas", null) {
    
    companion object {
        private val LOG = Logger.getInstance(AddToCanvasWebAction::class.java)
    }
    
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        LOG.info("AddToCanvasWebAction.actionPerformed called")
        
        // Create a node from the current editor position
        val node = BookmarkService.createNodeFromCurrentPosition(project) ?: return

        // Add to canvas
        canvasPanel.addNodeComponent(node)
    }

    override fun update(@NotNull e: AnActionEvent) {
        // Only enable the action when we have an open editor
        e.presentation.isEnabledAndVisible = (
                e.getData(CommonDataKeys.EDITOR) != null
        )
    }
}