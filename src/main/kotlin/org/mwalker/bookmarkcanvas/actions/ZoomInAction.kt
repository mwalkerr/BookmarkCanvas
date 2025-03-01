package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.annotations.NotNull

class ZoomInAction(
    private val canvasPanel: CanvasPanel
) : AnAction("Zoom In", "Increase canvas zoom", AllIcons.Graph.ZoomIn) {

    override fun actionPerformed(@NotNull e: AnActionEvent) {
        canvasPanel.zoomIn()
    }
}