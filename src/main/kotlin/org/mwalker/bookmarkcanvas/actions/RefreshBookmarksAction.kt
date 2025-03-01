package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.services.BookmarkService
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import org.mwalker.bookmarkcanvas.ui.CanvasPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull

class RefreshBookmarksAction(
    private val project: Project,
    private val canvasPanel: CanvasPanel
) : AnAction("Refresh Bookmarks", "Import all bookmarks to canvas", AllIcons.Actions.Refresh) {

    override fun actionPerformed(@NotNull e: AnActionEvent) {
        // Get all bookmarks from IDE
        val bookmarkNodes = BookmarkService.getAllBookmarkNodes(project)

        // Get current canvas state
        val canvasState = CanvasPersistenceService.getInstance().getCanvasState(project)

        // Add any new bookmarks (avoid duplicates)
        var changed = false
        for (node in bookmarkNodes) {
            if (!canvasState.nodes.containsKey(node.id)) {
                canvasState.addNode(node)
                canvasPanel.addNodeComponent(node)
                changed = true
            }
        }

        if (changed) {
            // Save state and refresh
            CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)
            canvasPanel.revalidate()
            canvasPanel.repaint()
        }
    }
}