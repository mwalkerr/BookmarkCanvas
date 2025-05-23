package org.mwalker.bookmarkcanvas.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.jetbrains.annotations.NotNull

class WebViewCanvasToolWindow : ToolWindowFactory {
    override fun createToolWindowContent(@NotNull project: Project, @NotNull toolWindow: ToolWindow) {
        // Create the web-based canvas toolbar
        val webCanvasToolbar = WebCanvasToolbar(project)

        val content = ContentFactory.SERVICE.getInstance().createContent(
            webCanvasToolbar, "", false)
        toolWindow.contentManager.addContent(content)
    }
}