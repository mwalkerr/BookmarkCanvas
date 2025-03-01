package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import org.jetbrains.annotations.NotNull

class ToggleSnapToGridAction(
    private val canvasPanel: CanvasPanel
) : AnAction("Snap to Grid", "Align nodes to a grid", AllIcons.Graph.Grid), Toggleable {

    private var snapToGrid = false

    override fun actionPerformed(@NotNull e: AnActionEvent) {
        snapToGrid = !snapToGrid
        canvasPanel.setSnapToGrid(snapToGrid)
        Toggleable.setSelected(e.presentation, snapToGrid)
    }

    override fun update(@NotNull e: AnActionEvent) {
        super.update(e)
        Toggleable.setSelected(e.presentation, snapToGrid)
    }
}