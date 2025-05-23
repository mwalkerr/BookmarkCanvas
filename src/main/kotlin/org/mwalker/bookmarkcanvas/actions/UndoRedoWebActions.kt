package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasInterface
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull

class UndoWebAction(
    private val project: Project,
    private val canvasPanel: CanvasInterface
) : AnAction("Undo", "Undo last action", null) {
    
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        canvasPanel.undo()
    }
}

class RedoWebAction(
    private val project: Project,
    private val canvasPanel: CanvasInterface
) : AnAction("Redo", "Redo last undone action", null) {
    
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        canvasPanel.redo()
    }
}