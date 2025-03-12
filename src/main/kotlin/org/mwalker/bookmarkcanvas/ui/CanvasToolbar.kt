package org.mwalker.bookmarkcanvas.ui

import org.mwalker.bookmarkcanvas.actions.*
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import java.awt.Dimension

class CanvasToolbar(private val project: Project) : SimpleToolWindowPanel(true, true) {
    val canvasPanel: org.mwalker.bookmarkcanvas.ui.CanvasPanel
    
    /**
     * Finds a NodeComponent by BookmarkNode id
     */
    fun findNodeComponent(nodeId: String): NodeComponent? {
        return canvasPanel.nodeComponents[nodeId]
    }

    init {
        // Create the canvas panel
        canvasPanel = org.mwalker.bookmarkcanvas.ui.CanvasPanel(project)

        // No scroll pane - relying on canvas panning and zooming
        
        // Create toolbar actions
        val actionGroup = DefaultActionGroup("CANVAS_TOOLBAR", false)
        // Add editing actions
        actionGroup.add(UndoAction(canvasPanel))
        actionGroup.add(RedoAction(canvasPanel))
        
        // Add separator
        actionGroup.addSeparator()
        
        // Add other actions
        actionGroup.add(ToggleSnapToGridAction(canvasPanel))
        actionGroup.add(ToggleShowGridAction(canvasPanel))
        actionGroup.add(ExportCanvasAction(project, canvasPanel))
        
        // Add separator for bookmark management
        actionGroup.addSeparator()
        
        // Add refresh and verify actions
//        actionGroup.add(RefreshBookmarksAction(project, canvasPanel))
        actionGroup.add(org.mwalker.bookmarkcanvas.actions.VerifyBookmarksAction(project, canvasPanel))
        
        // Add separator for zoom and navigation
        actionGroup.addSeparator()
        
        actionGroup.add(org.mwalker.bookmarkcanvas.actions.HomeAction(canvasPanel))
        actionGroup.add(org.mwalker.bookmarkcanvas.actions.ZoomInAction(canvasPanel))
        actionGroup.add(ZoomOutAction(canvasPanel))

        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("BookmarkCanvasToolbar", actionGroup, true)
        
        // Set the target component to fix the warning
        actionToolbar.setTargetComponent(canvasPanel)

        // Set the toolbar and content
        toolbar = actionToolbar.component
        setContent(canvasPanel)
//        setContent(KotlinSnippetHighlighter(project))
    }
}