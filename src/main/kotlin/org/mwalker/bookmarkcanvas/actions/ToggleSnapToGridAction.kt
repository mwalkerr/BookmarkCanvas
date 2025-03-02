package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasPanel
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import org.jetbrains.annotations.NotNull

class ToggleSnapToGridAction(
    private val canvasPanel: CanvasPanel
) : AnAction("Snap to Grid", "Align nodes to a grid", AllIcons.Graph.SnapToGrid), Toggleable {

    override fun actionPerformed(@NotNull e: AnActionEvent) {
        val newValue = !canvasPanel.snapToGrid
        canvasPanel.setSnapToGrid(newValue)
        Toggleable.setSelected(e.presentation, newValue)
        
        // Save state after changing preference
        val project = e.project ?: return
        val service = CanvasPersistenceService.getInstance()
        service.saveCanvasState(project, canvasPanel.canvasState)
    }

    override fun update(@NotNull e: AnActionEvent) {
        super.update(e)
        Toggleable.setSelected(e.presentation, canvasPanel.snapToGrid)
    }
}