package org.mwalker.bookmarkcanvas.model

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.awt.Point
import java.util.*

data class BookmarkNode(
    val id: String = UUID.randomUUID().toString(),
    val bookmarkId: String,
    var displayName: String,
    var filePath: String,
    var lineNumber: Int,
    var lineContent: String? = null, // Content of the line for relocation
    var positionX: Int = 100, // Default X position
    var positionY: Int = 100, // Default Y position
    var showCodeSnippet: Boolean = false,
    var contextLinesBefore: Int = 3,
    var contextLinesAfter: Int = 3
) {
    
    // Provide position as a computed property
    var position: Point
        get() = Point(positionX, positionY)
        set(value) {
            positionX = value.x
            positionY = value.y
        }
    
    constructor(bookmarkId: String, displayName: String, filePath: String, lineNumber: Int) : this(
        id = UUID.randomUUID().toString(),
        bookmarkId = bookmarkId,
        displayName = displayName,
        filePath = filePath,
        lineNumber = lineNumber
    )

    fun getCodeSnippet(project: Project?): String {
        if (project == null) return "No project"

        val file = project.baseDir.findFileByRelativePath(filePath)
            ?: return "File not found"

        val psiFile = PsiManager.getInstance(project).findFile(file)
            ?: return "Cannot read file"

        val document = psiFile.viewProvider.document
            ?: return "Cannot read document"

        val startLine = maxOf(0, lineNumber - contextLinesBefore)
        val endLine = minOf(document.lineCount - 1, lineNumber + contextLinesAfter)

        val startOffset = document.getLineStartOffset(startLine)
        val endOffset = document.getLineEndOffset(endLine)

        // Get the content of the snippet
        val snippetText = document.getText(TextRange(startOffset, endOffset))
        
        // Update line content if it's null
        if (lineContent == null && startLine <= lineNumber && lineNumber <= endLine) {
            val lineStart = document.getLineStartOffset(lineNumber)
            val lineEnd = document.getLineEndOffset(lineNumber)
            lineContent = document.getText(TextRange(lineStart, lineEnd))
        }
        
        // Normalize indentation to start at 0
        return normalizeIndentation(snippetText)
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
        val file = project.baseDir.findFileByRelativePath(filePath)
        if (file != null) {
            // Open the file in the editor
            val fileEditorManager = FileEditorManager.getInstance(project)
            fileEditorManager.openFile(file, true)
            
            // Get the document from the file
            val psiFile = PsiManager.getInstance(project).findFile(file)
            if (psiFile != null) {
                val document = psiFile.viewProvider.document
                if (document != null) {
                    // First try to find exact line
                    if (lineNumber >= 0 && lineNumber < document.lineCount) {
                        navigateToLine(fileEditorManager, document, lineNumber)
                    } 
                    // If we stored the line content, try to find by content
                    else if (lineContent != null && lineContent!!.isNotBlank()) {
                        findLineByContent(fileEditorManager, document, lineContent!!)
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
    private fun findLineByContent(fileEditorManager: FileEditorManager, document: Document, content: String) {
        val text = document.text
        val contentLines = content.trim().split("\n")
        
        // For multi-line content, search only for the first line
        val searchContent = contentLines.first().trim()
        if (searchContent.isEmpty()) return
        
        // Find the content in the document
        val index = text.indexOf(searchContent)
        if (index >= 0) {
            val lineNumber = document.getLineNumber(index)
            navigateToLine(fileEditorManager, document, lineNumber)
        }
    }
}