package org.mwalker.bookmarkcanvas.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.jetbrains.annotations.NotNull

class CanvasToolWindow : ToolWindowFactory {
    override fun createToolWindowContent(@NotNull project: Project, @NotNull toolWindow: ToolWindow) {
        // Create the toolbar panel instead of just the canvas
        val canvasToolbar = org.mwalker.bookmarkcanvas.ui.CanvasToolbar(project)

        val content = ContentFactory.SERVICE.getInstance().createContent(
            canvasToolbar, "", false)
        toolWindow.contentManager.addContent(content)
    }
}