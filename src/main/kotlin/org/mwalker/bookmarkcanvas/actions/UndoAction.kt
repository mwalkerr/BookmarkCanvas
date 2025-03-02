package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasPanel
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import org.jetbrains.annotations.NotNull

class UndoAction() : AnAction("Undo", "Undo the last action", AllIcons.Actions.Undo), DumbAware {

    constructor(canvasPanel: CanvasPanel) : this() {
        // Constructor for toolbar actions
        this.canvasPanel = canvasPanel
    }
    
    private var canvasPanel: CanvasPanel? = null
    
    override fun actionPerformed(@NotNull e: AnActionEvent) {
        // This action only works when called from the toolbar where canvasPanel is set
        val panel = canvasPanel ?: return
        val project = e.project ?: return
        val canvasState = panel.canvasState
        
        if (canvasState.undo()) {
            // After undoing, refresh the canvas
            panel.refreshFromState()
            
            // Save the updated state
            val service = CanvasPersistenceService.getInstance()
            service.saveCanvasState(project, canvasState)
        }
    }

    override fun update(@NotNull e: AnActionEvent) {
        // Only enable if we have a panel reference
        val panel = canvasPanel
        e.presentation.isEnabled = panel != null && panel.canvasState.canUndo()
    }
}