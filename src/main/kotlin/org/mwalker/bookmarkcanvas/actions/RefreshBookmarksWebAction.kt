package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasInterface
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull

class RefreshBookmarksWebAction(
    private val project: Project,
    private val canvasPanel: CanvasInterface
) : AnAction("Refresh Bookmarks", "Refresh bookmark positions", null) {
    
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        canvasPanel.refreshFromState()
    }
}