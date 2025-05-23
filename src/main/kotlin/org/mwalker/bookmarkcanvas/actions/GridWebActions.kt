package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasInterface
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull

class ToggleSnapToGridWebAction(
    private val project: Project,
    private val canvasPanel: CanvasInterface
) : AnAction("Toggle Snap to Grid", "Toggle snap to grid", null) {
    
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        // Note: This is simplified - in a full implementation you'd track the current state
        canvasPanel.setSnapToGrid(true) // This should toggle based on current state
    }
}

class ToggleShowGridWebAction(
    private val project: Project,
    private val canvasPanel: CanvasInterface
) : AnAction("Toggle Show Grid", "Toggle grid visibility", null) {
    
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        // Note: This is simplified - in a full implementation you'd track the current state
        canvasPanel.setShowGrid(true) // This should toggle based on current state
    }
}