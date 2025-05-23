package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasInterface
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull

class ZoomInWebAction(
    private val project: Project,
    private val canvasPanel: CanvasInterface
) : AnAction("Zoom In", "Zoom in on canvas", null) {
    
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        canvasPanel.zoomIn()
    }
}

class ZoomOutWebAction(
    private val project: Project,
    private val canvasPanel: CanvasInterface
) : AnAction("Zoom Out", "Zoom out on canvas", null) {
    
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        canvasPanel.zoomOut()
    }
}

class HomeWebAction(
    private val project: Project,
    private val canvasPanel: CanvasInterface
) : AnAction("Home", "Go to home position", null) {
    
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        canvasPanel.goHome()
    }
}