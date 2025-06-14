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
        // Skip if no line content is saved for either single-line or multi-line bookmarks
        if (node.lineContent.isNullOrBlank() && node.multiLineContent.isNullOrEmpty()) {
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
                
            // Handle multi-line bookmarks first
            if (node.showCodeSnippet && node.contextLinesAfter > 0 && 
                !node.multiLineContent.isNullOrEmpty()) {
                
                // Check if multi-line content still matches at current location
                if (checkMultiLineContentMatch(document, node.lineNumber0Based, node.multiLineContent!!)) {
                    return true
                }
                
                // Try to find the multi-line content elsewhere
                val newStartLine = findMultiLineContentByContent(document, node.multiLineContent!!, node.lineNumber0Based)
                if (newStartLine != null) {
                    LOG.info("Updated multi-line bookmark from line ${node.lineNumber0Based} to $newStartLine")
                    node.lineNumber0Based = newStartLine
                    // Refresh content after line number change
                    node.refreshContent(project)
                    return true
                }
                
                return false
            }
            
            // Handle single-line bookmarks
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
                // Refresh content after line number change
                node.refreshContent(project)
                return true
            }
            
            // No match found
            return false
        } catch (e: Exception) {
            LOG.error("Error verifying bookmark line", e)
            return false
        }
    }
    
    /**
     * Checks if the multi-line content still matches at the expected location
     */
    private fun checkMultiLineContentMatch(document: com.intellij.openapi.editor.Document, startLine: Int, expectedContent: List<String>): Boolean {
        if (startLine < 0 || startLine >= document.lineCount) return false
        
        val documentLines = document.text.lines()
        for (i in expectedContent.indices) {
            val currentLine = startLine + i
            if (currentLine >= documentLines.size) return false
            
            val expectedLine = expectedContent[i].trim()
            val actualLine = documentLines[currentLine].trim()
            
            if (expectedLine != actualLine) return false
        }
        return true
    }
    
    /**
     * Finds where the multi-line content block has moved to in the document
     */
    private fun findMultiLineContentByContent(document: com.intellij.openapi.editor.Document, expectedContent: List<String>, originalLineNumber: Int): Int? {
        if (expectedContent.isEmpty()) return null
        
        val documentLines = document.text.lines()
        val contentSize = expectedContent.size
        
        // Search for the first line and then verify the rest of the block
        val firstLineContent = expectedContent[0].trim()
        if (firstLineContent.isEmpty()) return null
        
        var bestMatch: Int? = null
        var minDistance = Int.MAX_VALUE
        
        for (startLine in 0 until documentLines.size - contentSize + 1) {
            if (documentLines[startLine].trim() == firstLineContent) {
                // Check if the entire block matches at this position
                var allMatch = true
                for (i in expectedContent.indices) {
                    val lineIndex = startLine + i
                    if (lineIndex >= documentLines.size) {
                        allMatch = false
                        break
                    }
                    
                    val expectedLine = expectedContent[i].trim()
                    val actualLine = documentLines[lineIndex].trim()
                    
                    if (expectedLine != actualLine) {
                        allMatch = false
                        break
                    }
                }
                
                if (allMatch) {
                    val distance = kotlin.math.abs(startLine - originalLineNumber)
                    if (distance < minDistance) {
                        minDistance = distance
                        bestMatch = startLine
                    }
                }
            }
        }
        
        return bestMatch
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