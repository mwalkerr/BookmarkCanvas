package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasInterface
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull

class ExportCanvasWebAction(
    private val project: Project,
    private val canvasPanel: CanvasInterface
) : AnAction("Export Canvas", "Export canvas data", null) {
    
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        // TODO: Implement web-based export functionality
        // This would need to be implemented differently from the AWT version
    }
}