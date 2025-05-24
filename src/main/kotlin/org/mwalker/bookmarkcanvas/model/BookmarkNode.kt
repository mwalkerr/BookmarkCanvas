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
    var multiLineContent: List<String>? = null, // Content of all lines for multi-line bookmarks
    var positionX: Int = 100, // Default X position
    var positionY: Int = 100, // Default Y position
    var width: Int = 0, // Width of the node (0 means use default size)
    var height: Int = 0, // Height of the node (0 means use default size)
    var showCodeSnippet: Boolean = false,
    var contextLinesBefore: Int = 3,
    var contextLinesAfter: Int = 3,
    var isValidBookmark: Boolean = true // Flag to indicate if the bookmark is valid
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
        lineNumber0Based = lineNumber,
        multiLineContent = null
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
        
        // Update line content and multi-line content if needed
        if (lineContent == null && startLine <= lineNumber0Based && lineNumber0Based <= endLine) {
            val lineStart = document.getLineStartOffset(lineNumber0Based)
            val lineEnd = document.getLineEndOffset(lineNumber0Based)
            lineContent = document.getText(TextRange(lineStart, lineEnd))
        }
        
        // For multi-line bookmarks (when showing code snippet with context lines), capture all lines
        if (showCodeSnippet && contextLinesAfter > 0 && multiLineContent == null) {
            val actualEndLine = minOf(document.lineCount - 1, lineNumber0Based + contextLinesAfter)
            val capturedLines = mutableListOf<String>()
            for (line in lineNumber0Based..actualEndLine) {
                val lineStart = document.getLineStartOffset(line)
                val lineEnd = document.getLineEndOffset(line)
                capturedLines.add(document.getText(TextRange(lineStart, lineEnd)))
            }
            multiLineContent = capturedLines
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
//        return text
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
        LOG.info("now navigating to bookmark for file: $filePath, line: ${lineNumber0Based + 1}")
        // Handle both absolute paths and project-relative paths
        val file = if (filePath.startsWith("/") || filePath.contains(":\\")) {
            // Absolute path
            com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath)
        } else {
            // Project-relative path
            project.baseDir.findFileByRelativePath(filePath)
        }
        if (file == null) {
            LOG.warn("File not found: $filePath")
            return
        }
        // Open the file in the editor
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFile(file, true)

        // Get the document from the file
        val psiFile = PsiManager.getInstance(project).findFile(file)
        if (psiFile == null) {
            LOG.warn("Cannot read file: $filePath")
            return
        }
        val document = psiFile.viewProvider.document
        if (document == null) {
            LOG.warn("File not found: $filePath")
            return
        }
        // If this is a selected text bookmark with multiple lines, first check relocation then select those lines
        if (showCodeSnippet && contextLinesAfter > 0) {
//            LOG.info("Navigating to multi-line bookmark at line $lineNumber0Based with context lines after: $contextLinesAfter")
            // First, check if we need to relocate using multi-line content
            if (multiLineContent != null && multiLineContent!!.isNotEmpty()) {
//                LOG.info("Checking multi-line content for relocation at line $lineNumber0Based")
                val currentLinesMatch = checkMultiLineContentMatch(document, lineNumber0Based, multiLineContent!!)
//                LOG.info("Current lines match: $currentLinesMatch")
                if (!currentLinesMatch) {
//                    LOG.info("Multi-line content no longer matches for selection, trying to relocate")
                    findMultiLineContentByContent(document, multiLineContent!!, lineNumber0Based)?.let { newStartLine ->
//                        LOG.info("Found multi-line content moved from $lineNumber0Based to $newStartLine for selection")
                        // Validate the new line number is within bounds
                        if (newStartLine >= 0 && newStartLine < document.lineCount) {
                            this.lineNumber0Based = newStartLine
                        } else {
                            LOG.warn("New start line $newStartLine is out of bounds for document with ${document.lineCount} lines")
                        }
                        
                        // Refresh node UI if displayName is null (using default name)
                        if (displayName == null) {
                            SwingUtilities.invokeLater {
                                findNodeComponentAndUpdate(project)
                            }
                        }
                    }
                }
            }
            
            try {
                val startLine = lineNumber0Based
                val endLine = minOf(lineNumber0Based + contextLinesAfter, document.lineCount - 1)

                // Validate line bounds
                if (startLine >= document.lineCount || startLine < 0) {
                    LOG.warn("Start line $startLine is out of bounds for document with ${document.lineCount} lines")
                    return
                }

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
                    
                    // Compare current code snippet with stored content to detect changes
                    val snippetChanged = hasCodeSnippetChanged(document)
                    
                    // Refresh content after navigation
                    refreshContent(project)
                    
                    // If snippet content changed, invalidate cache and update UI
                    if (snippetChanged) {
                        invalidateCacheAndUpdateUI(project)
                    }
                    
                    return
                }
            } catch (e: Exception) {
                LOG.error("Error selecting lines", e)
                // Fall back to regular navigation if selection fails
            }
        }

        // Regular bookmark handling
        var relocated = false
        
        // First, try to relocate using multi-line content if available
        if (showCodeSnippet && contextLinesAfter > 0 && multiLineContent != null && multiLineContent!!.isNotEmpty()) {
            val currentLinesMatch = checkMultiLineContentMatch(document, lineNumber0Based, multiLineContent!!)
            if (!currentLinesMatch) {
                LOG.info("Multi-line content no longer matches, trying to relocate")
                findMultiLineContentByContent(document, multiLineContent!!, lineNumber0Based)?.let { newStartLine ->
                    LOG.info("Found multi-line content moved from $lineNumber0Based to $newStartLine")
                    // Validate the new line number is within bounds
                    if (newStartLine >= 0 && newStartLine < document.lineCount) {
                        this.lineNumber0Based = newStartLine
                        relocated = true
                    } else {
                        LOG.warn("New start line $newStartLine is out of bounds for document with ${document.lineCount} lines")
                    }
                    
                    // Refresh node UI if displayName is null (using default name)
                    if (displayName == null) {
                        SwingUtilities.invokeLater {
                            findNodeComponentAndUpdate(project)
                        }
                    }
                }
            } else {
                // Multi-line content still matches at current location
                relocated = true
            }
        }
        
        // If multi-line relocation failed or not applicable, try single-line relocation
        if (!relocated && lineContent != null && lineContent!!.isNotBlank()) {
            val currentLineContent = document.text.split("\n").getOrNull(lineNumber0Based)?.trim()
            val storedLineContent = lineContent?.trim()
            
            // If content doesn't match, try to find by content
            if (currentLineContent != storedLineContent) {
                LOG.info("Content at line $lineNumber0Based no longer matches, trying to find by content")
                findLineNumber0BasedByContent(document, lineContent!!)?.let { newLineNumber ->
                    // Update the line number to the new position
                    if (newLineNumber != this.lineNumber0Based) {
                        LOG.info("Updating line number from ${this.lineNumber0Based} to $newLineNumber")
                        // Validate the new line number is within bounds
                        if (newLineNumber >= 0 && newLineNumber < document.lineCount) {
                            this.lineNumber0Based = newLineNumber
                            relocated = true
                        } else {
                            LOG.warn("New line number $newLineNumber is out of bounds for document with ${document.lineCount} lines")
                        }

                        // Refresh node UI if displayName is null (using default name)
                        if (displayName == null) {
                            // Find component in UI tree and update it
                            SwingUtilities.invokeLater {
                                findNodeComponentAndUpdate(project)
                            }
                        }
                    }
                }
            } else {
                // Single-line content still matches
                relocated = true
            }
        }
        
        // Compare current code snippet with stored content to detect changes
        val snippetChanged = hasCodeSnippetChanged(document)
        
        // Always refresh the current content from the file (after potential relocation)
        refreshContent(project)
        
        // If snippet content changed, invalidate cache and update UI
        if (snippetChanged) {
            invalidateCacheAndUpdateUI(project)
        }
        
        navigateToLine(fileEditorManager, document, lineNumber0Based)
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
     * Checks if the multi-line content still matches at the expected location
     */
    private fun checkMultiLineContentMatch(document: Document, startLine: Int, expectedContent: List<String>): Boolean {
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
    private fun findMultiLineContentByContent(document: Document, expectedContent: List<String>, originalLineNumber: Int): Int? {
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
    
    /**
     * Refreshes the stored content for this bookmark from the current file state
     */
    fun refreshContent(project: Project) {
        LOG.info("Refreshing content for bookmark at file: $filePath, line: ${lineNumber0Based + 1}")
        try {
            // Handle both absolute paths and project-relative paths
            val file = if (filePath.startsWith("/") || filePath.contains(":\\")) {
                // Absolute path
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath)
            } else {
                // Project-relative path
                project.baseDir.findFileByRelativePath(filePath)
            } ?: return
            
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
            val document = psiFile.viewProvider.document ?: return
            
            // Update single line content
            if (lineNumber0Based >= 0 && lineNumber0Based < document.lineCount) {
                val lineStart = document.getLineStartOffset(lineNumber0Based)
                val lineEnd = document.getLineEndOffset(lineNumber0Based)
                lineContent = document.getText(TextRange(lineStart, lineEnd))
                LOG.info("Updated line content for bookmark at line ${lineNumber0Based + 1}: $lineContent")
            }
            
            // Update multi-line content for multi-line bookmarks
            if (showCodeSnippet && contextLinesAfter > 0) {
                val actualEndLine = minOf(document.lineCount - 1, lineNumber0Based + contextLinesAfter)
                val capturedLines = mutableListOf<String>()
                
                // Validate bounds before capturing lines
                if (lineNumber0Based >= 0 && lineNumber0Based < document.lineCount) {
                    for (line in lineNumber0Based..actualEndLine) {
                        val lineStart = document.getLineStartOffset(line)
                        val lineEnd = document.getLineEndOffset(line)
                        val text = document.getText(TextRange(lineStart, lineEnd)).trim()
                        LOG.info("Captured line $line: $text")
                        capturedLines.add(text)
                    }
                    multiLineContent = capturedLines
                }
            }
            
            LOG.info("Refreshed content for bookmark at line ${lineNumber0Based + 1}")
        } catch (e: Exception) {
            LOG.error("Error refreshing bookmark content", e)
        }
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
    
    /**
     * Checks if the current code snippet in the file differs from what was previously stored
     */
    private fun hasCodeSnippetChanged(document: Document): Boolean {
        try {
            // Only check snippet changes for nodes that show code snippets
            if (!showCodeSnippet) {
                // For non-snippet nodes, just check the main line content
                if (lineContent != null && lineNumber0Based >= 0 && lineNumber0Based < document.lineCount) {
                    val lineStart = document.getLineStartOffset(lineNumber0Based)
                    val lineEnd = document.getLineEndOffset(lineNumber0Based)
                    val currentLineText = document.getText(TextRange(lineStart, lineEnd)).trimEnd()
                    val storedLineText = lineContent!!.trimEnd()
                    return currentLineText != storedLineText
                }
                return false
            }
            
            // Get the current snippet from the document
            val startLine = maxOf(0, lineNumber0Based - contextLinesBefore)
            val endLine = minOf(document.lineCount - 1, lineNumber0Based + contextLinesAfter)
            
            if (startLine >= document.lineCount || lineNumber0Based < 0) {
                return true // File structure changed significantly
            }
            
            val currentLines = mutableListOf<String>()
            for (line in startLine..endLine) {
                if (line < document.lineCount) {
                    val lineStart = document.getLineStartOffset(line)
                    val lineEnd = document.getLineEndOffset(line)
                    val lineText = document.getText(TextRange(lineStart, lineEnd)).trimEnd()
                    currentLines.add(lineText)
                }
            }
            
            // Get stored content for comparison
            val storedLines = getStoredSnippetLines()
            if (storedLines.isEmpty()) {
                return true // No previous content stored, consider it changed
            }
            
            // Compare line by line
            if (currentLines.size != storedLines.size) {
                LOG.info("Code snippet changed: line count differs (${currentLines.size} vs ${storedLines.size})")
                return true
            }
            
            for (i in currentLines.indices) {
                val currentLine = currentLines[i].trimEnd()
                val storedLine = storedLines[i].trimEnd()
                if (currentLine != storedLine) {
                    LOG.info("Code snippet changed at line ${startLine + i}: '$storedLine' -> '$currentLine'")
                    return true
                }
            }
            
            return false
        } catch (e: Exception) {
            LOG.error("Error checking if code snippet changed", e)
            return true // Assume changed if we can't determine
        }
    }
    
    /**
     * Gets the stored snippet lines for comparison
     */
    private fun getStoredSnippetLines(): List<String> {
        // For multi-line bookmarks with stored content, return the stored lines
        if (showCodeSnippet && multiLineContent != null && multiLineContent!!.isNotEmpty()) {
            return multiLineContent!!
        }
        
        // For single-line bookmarks, we need to reconstruct the context
        // This is an approximation - we'll return what we can from stored content
        if (lineContent != null) {
            return listOf(lineContent!!)
        }
        
        return emptyList()
    }
    
    /**
     * Invalidates the UI cache and updates the node component
     */
    private fun invalidateCacheAndUpdateUI(project: Project) {
        LOG.info("Code snippet changed for node ${id}, invalidating cache and updating UI")
        SwingUtilities.invokeLater {
            try {
                // Invalidate the snippet cache
                val toolWindowManager = ToolWindowManager.getInstance(project)
                val toolWindow = toolWindowManager.getToolWindow("BookmarkCanvas")
                if (toolWindow != null) {
                    val content = toolWindow.contentManager.getContent(0)
                    if (content != null) {
                        val canvasToolbar = content.component as? org.mwalker.bookmarkcanvas.ui.CanvasToolbar
                        canvasToolbar?.let { toolbar ->
                            // Find the node component and invalidate its cache
                            toolbar.findNodeComponent(this.id)?.let { nodeComponent ->
                                // Use the NodeUIManager to invalidate cache
                                org.mwalker.bookmarkcanvas.ui.NodeUIManager.invalidateSnippetCache(this.id)
                                
                                // Refresh the component layout
                                if (nodeComponent is org.mwalker.bookmarkcanvas.ui.NodeComponent) {
                                    nodeComponent.refreshLayout()
                                    nodeComponent.repaint()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                LOG.error("Error invalidating cache and updating UI", e)
            }
        }
    }
}