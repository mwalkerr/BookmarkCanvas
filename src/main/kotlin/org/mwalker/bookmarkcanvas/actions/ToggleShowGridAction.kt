package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasPanel
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import org.jetbrains.annotations.NotNull

class ToggleShowGridAction(
    private val canvasPanel: CanvasPanel
) : AnAction("Show Grid", "Show or hide the grid", AllIcons.Graph.Grid), Toggleable {

    override fun actionPerformed(@NotNull e: AnActionEvent) {
        val newValue = !canvasPanel.showGrid
        canvasPanel.setShowGrid(newValue)
        Toggleable.setSelected(e.presentation, newValue)
        
        // Save state after changing preference
        val project = e.project ?: return
        val service = CanvasPersistenceService.getInstance()
        service.saveCanvasState(project, canvasPanel.canvasState)
    }

    override fun update(@NotNull e: AnActionEvent) {
        super.update(e)
        Toggleable.setSelected(e.presentation, canvasPanel.showGrid)
    }
}