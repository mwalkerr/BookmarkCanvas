package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasInterface
import org.mwalker.bookmarkcanvas.services.BookmarkService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull

class AddFileToCanvasWebAction(
    private val project: Project,
    private val canvasPanel: CanvasInterface
) : AnAction("Add File to Canvas", "Add current file to canvas", null) {
    
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        // Create a file node from the current file
        val node = BookmarkService.createFileNodeFromCurrentFile(project) ?: return
        
        // Add to canvas
        canvasPanel.addNodeComponent(node)
    }

    override fun update(@NotNull e: AnActionEvent) {
        // Only enable the action when we have an open file
        e.presentation.isEnabledAndVisible = (
                e.getData(CommonDataKeys.VIRTUAL_FILE) != null
        )
    }
}