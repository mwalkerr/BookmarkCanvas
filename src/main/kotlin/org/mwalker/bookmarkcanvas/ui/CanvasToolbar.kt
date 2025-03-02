package org.mwalker.bookmarkcanvas.ui

import org.mwalker.bookmarkcanvas.actions.*
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import javax.swing.JScrollPane
import java.awt.Dimension

class CanvasToolbar(private val project: Project) : SimpleToolWindowPanel(true, true) {
    private val canvasPanel: org.mwalker.bookmarkcanvas.ui.CanvasPanel

    init {
        // Create the canvas panel
        canvasPanel = org.mwalker.bookmarkcanvas.ui.CanvasPanel(project)
        val scrollPane = JScrollPane(canvasPanel)
        scrollPane.verticalScrollBar.unitIncrement = 16
        scrollPane.horizontalScrollBar.unitIncrement = 16

        // Allow the canvas to be larger than the visible area
        canvasPanel.preferredSize = Dimension(5000, 5000)

        // Create toolbar actions
        val actionGroup = DefaultActionGroup("CANVAS_TOOLBAR", false)
        // Add editing actions
        actionGroup.add(UndoAction(canvasPanel))
        actionGroup.add(RedoAction(canvasPanel))
        
        // Add separator
        actionGroup.addSeparator()
        
        // Add other actions
        actionGroup.add(org.mwalker.bookmarkcanvas.actions.RefreshBookmarksAction(project, canvasPanel))
        actionGroup.add(ClearCanvasAction(project, canvasPanel))
        actionGroup.add(ToggleSnapToGridAction(canvasPanel))
        actionGroup.add(ToggleShowGridAction(canvasPanel))
        actionGroup.add(ExportCanvasAction(project, canvasPanel))
        
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
        setContent(scrollPane)
    }
}