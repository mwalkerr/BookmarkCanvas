package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.annotations.NotNull

class ZoomOutAction(
    private val canvasPanel: CanvasPanel
) : AnAction("Zoom Out", "Decrease canvas zoom", AllIcons.Graph.ZoomOut) {

    override fun actionPerformed(@NotNull e: AnActionEvent) {
        canvasPanel.zoomOut()
    }
}