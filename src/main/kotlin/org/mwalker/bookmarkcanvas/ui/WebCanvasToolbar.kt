package org.mwalker.bookmarkcanvas.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import javax.swing.JToolBar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation

/**
 * Web-based canvas toolbar that contains the WebCanvasPanel and toolbar buttons
 */
class WebCanvasToolbar(private val project: Project) : JBPanel<WebCanvasToolbar>(BorderLayout()) {
    private val webCanvasPanel: WebCanvasPanel
    private val toolbar: JToolBar

    init {
        // Create the web canvas panel
        webCanvasPanel = WebCanvasPanel(project)
        
        // Create toolbar with web canvas actions
        toolbar = createToolbar()
        
        // Layout components
        add(toolbar, BorderLayout.NORTH)
        add(webCanvasPanel, BorderLayout.CENTER)
    }

    private fun createToolbar(): JToolBar {
        val toolbar = JToolBar()
        toolbar.isFloatable = false
        
        // Add web-compatible action buttons manually
        val addButton = toolbar.add(createAction("Add") { 
            org.mwalker.bookmarkcanvas.actions.AddToCanvasWebAction(project, webCanvasPanel).actionPerformed(createMockActionEvent()) 
        })
        addButton.toolTipText = "Add to Canvas"
        
        val addFileButton = toolbar.add(createAction("Add File") { 
            org.mwalker.bookmarkcanvas.actions.AddFileToCanvasWebAction(project, webCanvasPanel).actionPerformed(createMockActionEvent()) 
        })
        addFileButton.toolTipText = "Add File to Canvas"
        
        toolbar.addSeparator()
        
        val clearButton = toolbar.add(createAction("Clear") { 
            org.mwalker.bookmarkcanvas.actions.ClearCanvasWebAction(project, webCanvasPanel).actionPerformed(createMockActionEvent()) 
        })
        clearButton.toolTipText = "Clear Canvas"
        
        val refreshButton = toolbar.add(createAction("Refresh") { 
            org.mwalker.bookmarkcanvas.actions.RefreshBookmarksWebAction(project, webCanvasPanel).actionPerformed(createMockActionEvent()) 
        })
        refreshButton.toolTipText = "Refresh Bookmarks"
        
        toolbar.addSeparator()
        
        val undoButton = toolbar.add(createAction("Undo") { 
            org.mwalker.bookmarkcanvas.actions.UndoWebAction(project, webCanvasPanel).actionPerformed(createMockActionEvent()) 
        })
        undoButton.toolTipText = "Undo"
        
        val redoButton = toolbar.add(createAction("Redo") { 
            org.mwalker.bookmarkcanvas.actions.RedoWebAction(project, webCanvasPanel).actionPerformed(createMockActionEvent()) 
        })
        redoButton.toolTipText = "Redo"
        
        toolbar.addSeparator()
        
        val zoomInButton = toolbar.add(createAction("Zoom In") { 
            org.mwalker.bookmarkcanvas.actions.ZoomInWebAction(project, webCanvasPanel).actionPerformed(createMockActionEvent()) 
        })
        zoomInButton.toolTipText = "Zoom In"
        
        val zoomOutButton = toolbar.add(createAction("Zoom Out") { 
            org.mwalker.bookmarkcanvas.actions.ZoomOutWebAction(project, webCanvasPanel).actionPerformed(createMockActionEvent()) 
        })
        zoomOutButton.toolTipText = "Zoom Out"
        
        val homeButton = toolbar.add(createAction("Home") { 
            org.mwalker.bookmarkcanvas.actions.HomeWebAction(project, webCanvasPanel).actionPerformed(createMockActionEvent()) 
        })
        homeButton.toolTipText = "Go Home"
        
        toolbar.addSeparator()
        
        val snapGridButton = toolbar.add(createAction("Snap Grid") { 
            org.mwalker.bookmarkcanvas.actions.ToggleSnapToGridWebAction(project, webCanvasPanel).actionPerformed(createMockActionEvent()) 
        })
        snapGridButton.toolTipText = "Toggle Snap to Grid"
        
        val showGridButton = toolbar.add(createAction("Show Grid") { 
            org.mwalker.bookmarkcanvas.actions.ToggleShowGridWebAction(project, webCanvasPanel).actionPerformed(createMockActionEvent()) 
        })
        showGridButton.toolTipText = "Toggle Show Grid"
        
        toolbar.addSeparator()
        
        val exportButton = toolbar.add(createAction("Export") { 
            org.mwalker.bookmarkcanvas.actions.ExportCanvasWebAction(project, webCanvasPanel).actionPerformed(createMockActionEvent()) 
        })
        exportButton.toolTipText = "Export Canvas"
        
        return toolbar
    }
    
    private fun createAction(name: String, actionHandler: () -> Unit): javax.swing.Action {
        return object : javax.swing.AbstractAction(name) {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                actionHandler()
            }
        }
    }
    
    private fun createMockActionEvent(): AnActionEvent {
        return AnActionEvent.createFromDataContext(
            "MockAction",
            Presentation(),
            DataContext { dataId -> 
                when (dataId) {
                    "project" -> project
                    else -> null
                }
            }
        )
    }
}