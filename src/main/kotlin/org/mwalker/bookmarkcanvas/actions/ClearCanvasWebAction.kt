package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasInterface
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull

class ClearCanvasWebAction(
    private val project: Project,
    private val canvasPanel: CanvasInterface
) : AnAction("Clear Canvas", "Clear all nodes from canvas", null) {
    
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        canvasPanel.clearCanvas()
    }
}