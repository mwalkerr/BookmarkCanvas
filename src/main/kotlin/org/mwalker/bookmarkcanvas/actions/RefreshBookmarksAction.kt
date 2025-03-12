package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.services.BookmarkService
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import org.mwalker.bookmarkcanvas.ui.CanvasPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.NotNull
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications

/**
 * Helper class with shared utility methods for bookmark validation
 */
object BookmarkValidator {
    private val LOG = Logger.getInstance(BookmarkValidator::class.java)
    private val NOTIFICATION_GROUP = NotificationGroup.balloonGroup("Bookmark Canvas")
    
    /**
     * Verify all bookmarks in the canvas state to ensure line numbers are accurate
     * Returns the number of bookmarks that were updated or marked invalid
     */
    fun verifyAllBookmarks(project: Project, canvasState: org.mwalker.bookmarkcanvas.model.CanvasState): Int {
        var updatedCount = 0
        var validCount = 0
        var invalidCount = 0
        
        // Check all nodes in the canvas
        for (node in canvasState.nodes.values) {
            val wasValid = node.isValidBookmark
            val isValid = verifyBookmarkLineNumber(project, node)
            
            // If validity state changed, count it
            if (wasValid != isValid) {
                if (isValid) validCount++ else invalidCount++
                updatedCount++
            }
            
            // Update the node's valid state
            node.isValidBookmark = isValid
        }
        
        // Show notification with results
        val message = when {
            updatedCount == 0 -> "All bookmarks are up to date"
            invalidCount > 0 -> "Found $invalidCount invalid bookmarks (marked with red border)"
            else -> "Updated $validCount bookmarks"
        }
        
        NOTIFICATION_GROUP.createNotification(
            "Bookmark Verification", 
            message,
            if (invalidCount > 0) NotificationType.WARNING else NotificationType.INFORMATION,
            null
        ).notify(project)
        
        return updatedCount
    }
    
    /**
     * Verifies if a bookmark's line number is accurate and tries to relocate it if needed
     * Returns true if the bookmark is valid (either as-is or after relocation)
     */
    fun verifyBookmarkLineNumber(project: Project, node: BookmarkNode): Boolean {
        // Skip if no line content is saved
        if (node.lineContent.isNullOrBlank()) {
            return true // Assume valid if we don't have content to verify
        }
        
        try {
            // Handle both absolute paths and project-relative paths
            val file = if (node.filePath.startsWith("/") || node.filePath.contains(":\\")) {
                // Absolute path
                LocalFileSystem.getInstance().findFileByPath(node.filePath)
            } else {
                // Project-relative path
                project.baseDir.findFileByRelativePath(node.filePath)
            } ?: return false // File not found
            
            val psiFile = PsiManager.getInstance(project).findFile(file)
                ?: return false // Cannot read file
                
            val document = psiFile.viewProvider.document
                ?: return false // Cannot read document
                
            // Check if current line matches stored content
            val lineContent = if (node.lineNumber0Based >= 0 && node.lineNumber0Based < document.lineCount) {
                val lineStart = document.getLineStartOffset(node.lineNumber0Based)
                val lineEnd = document.getLineEndOffset(node.lineNumber0Based)
                document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
            } else {
                null
            }
            
            // If line content matches, bookmark is valid
            if (lineContent?.trim() == node.lineContent?.trim()) {
                return true
            }
            
            // Try to relocate
            val trimmedContent = node.lineContent?.trim() ?: return false
            val text = document.text
            val lines = text.lines()
            
            // Search for matching line
            var bestMatchLine: Int? = null
            var minDistance = Int.MAX_VALUE
            
            lines.forEachIndexed { index, line ->
                if (line.contains(trimmedContent)) {
                    val distance = kotlin.math.abs(index - node.lineNumber0Based)
                    if (distance < minDistance) {
                        minDistance = distance
                        bestMatchLine = index
                    }
                }
            }
            
            // Update line number if found
            if (bestMatchLine != null) {
                LOG.info("Updated bookmark line from ${node.lineNumber0Based} to $bestMatchLine")
                node.lineNumber0Based = bestMatchLine!!
                return true
            }
            
            // No match found
            return false
        } catch (e: Exception) {
            LOG.error("Error verifying bookmark line", e)
            return false
        }
    }
}

class RefreshBookmarksAction(
    private val project: Project,
    private val canvasPanel: CanvasPanel
) : AnAction("Refresh Bookmarks", "Import all bookmarks from IDE", AllIcons.Actions.Refresh) {
    private val LOG = Logger.getInstance(RefreshBookmarksAction::class.java)

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

        // Save state and refresh the canvas
        CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)
        canvasPanel.refreshFromState()
    }
}

/**
 * Action to verify all bookmarks on the canvas and update their status
 */
class VerifyBookmarksAction(
    private val project: Project,
    private val canvasPanel: CanvasPanel
) : AnAction("Verify Bookmarks", "Check all bookmarks for accuracy", AllIcons.Actions.Refresh) {

    override fun actionPerformed(@NotNull e: AnActionEvent) {
        // Get current canvas state
        val canvasState = CanvasPersistenceService.getInstance().getCanvasState(project)
        
        // Verify all bookmarks
        BookmarkValidator.verifyAllBookmarks(project, canvasState)
        
        // Save state and refresh UI
        CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)
        canvasPanel.refreshFromState()
    }
}