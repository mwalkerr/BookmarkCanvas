package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import org.jetbrains.annotations.NotNull

class ToggleShowGridAction(
    private val canvasPanel: CanvasPanel
) : AnAction("Show Grid", "Show or hide the grid", AllIcons.Graph.Grid), Toggleable {

    private var showGrid = false

    override fun actionPerformed(@NotNull e: AnActionEvent) {
        showGrid = !showGrid
        canvasPanel.setShowGrid(showGrid)
        Toggleable.setSelected(e.presentation, showGrid)
    }

    override fun update(@NotNull e: AnActionEvent) {
        super.update(e)
        Toggleable.setSelected(e.presentation, showGrid)
    }
}