package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import org.mwalker.bookmarkcanvas.ui.CanvasPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.NotNull

class ClearCanvasAction(
    private val project: Project,
    private val canvasPanel: CanvasPanel
) : AnAction("Clear Canvas", "Remove all nodes from canvas", AllIcons.Actions.GC) {

    override fun actionPerformed(@NotNull e: AnActionEvent) {
        val result = Messages.showYesNoDialog(
            "Are you sure you want to clear all nodes from the canvas?",
            "Clear Canvas",
            AllIcons.General.QuestionDialog
        )

        if (result == Messages.YES) {
            canvasPanel.clearCanvas()
        }
    }
}