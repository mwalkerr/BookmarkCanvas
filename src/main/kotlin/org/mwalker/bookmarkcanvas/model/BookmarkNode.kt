package org.mwalker.bookmarkcanvas.model

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiManager
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.SwingUtilities
import java.awt.Point
import java.util.*
import com.intellij.openapi.diagnostic.Logger


data class BookmarkNode(
    val id: String = UUID.randomUUID().toString(),
    val bookmarkId: String,
    var displayName: String? = null,
    var filePath: String,
    var lineNumber0Based: Int,
    var lineContent: String? = null, // Content of the line for relocation
    var positionX: Int = 100, // Default X position
    var positionY: Int = 100, // Default Y position
    var width: Int = 0, // Width of the node (0 means use default size)
    var height: Int = 0, // Height of the node (0 means use default size)
    var showCodeSnippet: Boolean = false,
    var contextLinesBefore: Int = 3,
    var contextLinesAfter: Int = 3
) {
    companion object {
        private val LOG = Logger.getInstance(BookmarkNode::class.java)
    }
    
    // Provide position as a computed property
    var position: Point
        get() = Point(positionX, positionY)
        set(value) {
            positionX = value.x
            positionY = value.y
        }
    
    constructor(bookmarkId: String, displayName: String?, filePath: String, lineNumber: Int) : this(
        id = UUID.randomUUID().toString(),
        bookmarkId = bookmarkId,
        displayName = displayName,
        filePath = filePath,
        lineNumber0Based = lineNumber
    )
    
    /**
     * Gets the display name for this node. If no custom name is set,
     * returns a default name based on filename and line number.
     * If lineNumber0Based is 0, only the filename is displayed.
     */
    fun getDisplayText(): String {
        return displayName ?: run {
            val fileName = filePath.substringAfterLast("/").substringAfterLast("\\")
            if (lineNumber0Based == 0) {
                fileName
            } else {
                "$fileName:${lineNumber0Based + 1}"
            }
        }
    }

    fun getCodeSnippet(project: Project?): String = kotlin.runCatching {
        if (project == null) return@runCatching "No project"
        
            // For selected text bookmarks, we want to show the entire selection with different formatting
        // We don't use a special property anymore, just control it with contextLines parameters
        
        // Regular bookmark approach - get snippet from file
        // Handle both absolute paths and project-relative paths
        val file = if (filePath.startsWith("/") || filePath.contains(":\\")) {
            // Absolute path
            com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath)
        } else {
            // Project-relative path
            project.baseDir.findFileByRelativePath(filePath)
        }
            ?: return@runCatching "File not found: $filePath"

        val psiFile = PsiManager.getInstance(project).findFile(file)
            ?: return@runCatching "Cannot read file"

        val document = psiFile.viewProvider.document
            ?: return@runCatching "Cannot read document"

        val startLine = maxOf(0, lineNumber0Based - contextLinesBefore)
        val endLine = minOf(document.lineCount - 1, lineNumber0Based + contextLinesAfter)

        val startOffset = document.getLineStartOffset(startLine)
        val endOffset = document.getLineEndOffset(endLine)

        // Get the content of the snippet
        val snippetText = document.getText(TextRange(startOffset, endOffset))
        
        // Update line content if it's null
        if (lineContent == null && startLine <= lineNumber0Based && lineNumber0Based <= endLine) {
            val lineStart = document.getLineStartOffset(lineNumber0Based)
            val lineEnd = document.getLineEndOffset(lineNumber0Based)
            lineContent = document.getText(TextRange(lineStart, lineEnd))
        }
        
        // Normalize indentation to start at 0
        val normalizedText = normalizeIndentation(snippetText)
        
        // Format each line with proper prefix
        return@runCatching formatWithLinePrefix(normalizedText, startLine, lineNumber0Based)
    }.onFailure {
        LOG.error("Error getting code snippet", it)
        "Error getting code snippet: ${it.message}"
    }.getOrNull() ?: ""
    
    /**
     * Formats code snippet text where each line is prefixed with "  " (two spaces)
     * and the bookmarked line is prefixed with "> " instead
     */
    private fun formatWithLinePrefix(text: String, startLine: Int, bookmarkedLine: Int): String {
        val lines = text.lines()
        val formattedLines = lines.mapIndexed { index, line ->
            val currentLineNumber = startLine + index
            if (currentLineNumber == bookmarkedLine) {
                "> $line" // Bookmarked line prefix
            } else {
                "  $line" // Regular line prefix
            }
        }
        return formattedLines.joinToString("\n")
    }
    
    /**
     * Normalizes the indentation of a code snippet so the minimum indentation level is 0
     */
    private fun normalizeIndentation(text: String): String {
        val lines = text.lines()
        if (lines.isEmpty()) return text
        
        // Find the minimum indentation across non-empty lines
        val minIndent = lines
            .filter { it.isNotBlank() }
            .minOfOrNull { line -> line.takeWhile { it.isWhitespace() }.length } ?: 0
        
        // Only need to process if there's indentation to remove
        if (minIndent == 0) return text
        
        // Remove exactly minIndent spaces from the beginning of each line
        return lines.joinToString("\n") { line ->
            if (line.isBlank()) line else line.substring(minOf(minIndent, line.takeWhile { it.isWhitespace() }.length))
        }
    }

    fun navigateToBookmark(project: Project) {
        // Handle both absolute paths and project-relative paths
        val file = if (filePath.startsWith("/") || filePath.contains(":\\")) {
            // Absolute path
            com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath)
        } else {
            // Project-relative path
            project.baseDir.findFileByRelativePath(filePath)
        }
        if (file != null) {
            // Open the file in the editor
            val fileEditorManager = FileEditorManager.getInstance(project)
            fileEditorManager.openFile(file, true)
            
            // Get the document from the file
            val psiFile = PsiManager.getInstance(project).findFile(file)
            if (psiFile != null) {
                val document = psiFile.viewProvider.document
                if (document != null) {
                    // If this is a selected text bookmark with multiple lines, select those lines
                    if (showCodeSnippet && contextLinesAfter > 0) {
                        try {
                            val startLine = lineNumber0Based
                            val endLine = lineNumber0Based + contextLinesAfter
                            
                            // Get the start and end offsets
                            val startOffset = document.getLineStartOffset(startLine)
                            val endOffset = document.getLineEndOffset(endLine)
                            
                            // Navigate and select
                            val editor = fileEditorManager.selectedTextEditor
                            if (editor != null) {
                                // Move to the start of the selection
                                editor.caretModel.moveToOffset(startOffset)
                                editor.selectionModel.setSelection(startOffset, endOffset)
                                editor.scrollingModel.scrollToCaret(
                                    com.intellij.openapi.editor.ScrollType.CENTER
                                )
                                return
                            }
                        } catch (e: Exception) {
                            LOG.error("Error selecting lines", e)
                            // Fall back to regular navigation if selection fails
                        }
                    }
                
                    // Regular bookmark handling
                    val currentLineContents = document.text.split("\n").getOrNull(lineNumber0Based)
                    // First try to find exact line
                    if (lineNumber0Based >= 0 && lineNumber0Based < document.lineCount && currentLineContents?.trim() == lineContent?.trim()) {
                        navigateToLine(fileEditorManager, document, lineNumber0Based)
                    } 
                    // If we stored the line content, try to find by content
                    else if (lineContent != null && lineContent!!.isNotBlank()) {
                        findLineNumber0BasedByContent(document, lineContent!!)?.let { lineNumber0Based ->
                            navigateToLine(fileEditorManager, document, lineNumber0Based)
                            // Update the line number to the new position
                            if (lineNumber0Based != this.lineNumber0Based) {
                                LOG.info("Updating line number from ${this.lineNumber0Based} to $lineNumber0Based")
                                this.lineNumber0Based = lineNumber0Based
                                
                                // Refresh node UI if displayName is null (using default name)
                                if (displayName == null) {
                                    // Find component in UI tree and update it
                                    SwingUtilities.invokeLater {
                                        findNodeComponentAndUpdate(project)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Navigate to a specific line in the document
     */
    private fun navigateToLine(fileEditorManager: FileEditorManager, document: Document, line: Int) {
        val offset = document.getLineStartOffset(line)
        val editor = fileEditorManager.selectedTextEditor
        if (editor != null) {
            editor.caretModel.moveToOffset(offset)
            editor.scrollingModel.scrollToCaret(
                com.intellij.openapi.editor.ScrollType.CENTER
            )
        }
    }
    
    /**
     * Try to find a line containing specific content and navigate to it
     */
    private fun findLineNumber0BasedByContent(document: Document, content: String): Int? {
        val text = document.text
        val searchContent = content.trim()
        if (searchContent.isEmpty()) return null

        val lines = text.lines()
        var closestLine: Int? = null
        var minDistance = Int.MAX_VALUE

        lines.forEachIndexed { index, line ->
            if (line.contains(searchContent)) {
                val distance = kotlin.math.abs(index - lineNumber0Based)
                if (distance < minDistance) {
                    minDistance = distance
                    closestLine = index
                }
            }
        }

        return closestLine
    }
    /**
     * Finds the NodeComponent in the UI tree that represents this BookmarkNode
     * and updates its display
     */
    private fun findNodeComponentAndUpdate(project: Project) {
        try {
            // Get the tool window manager
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val toolWindow = toolWindowManager.getToolWindow("BookmarkCanvas")
            if (toolWindow != null) {
                // Get content component (CanvasToolbar)
                val content = toolWindow.contentManager.getContent(0)
                if (content != null) {
                    val canvasToolbar = content.component as? org.mwalker.bookmarkcanvas.ui.CanvasToolbar
                    canvasToolbar?.let { toolbar ->
                        canvasToolbar.canvasPanel.saveState()
                        // Find the node component for this node
                        toolbar.findNodeComponent(this.id)?.let { nodeComponent ->
                            // Update the title
                            nodeComponent.updateTitle(this.getDisplayText())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log error but don't crash if UI update fails
            // This is a best-effort update
        }
    }
}