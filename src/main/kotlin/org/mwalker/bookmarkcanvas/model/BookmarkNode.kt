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

        return document.getText(TextRange(startOffset, endOffset))
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
                if (document != null && lineNumber >= 0 && lineNumber < document.lineCount) {
                    // Calculate the offset for the line number
                    val offset = document.getLineStartOffset(lineNumber - 1)
                    
                    // Get the text editor and navigate to the offset
                    val editor = fileEditorManager.selectedTextEditor
                    if (editor != null) {
                        editor.caretModel.moveToOffset(offset)
                        editor.scrollingModel.scrollToCaret(
                            com.intellij.openapi.editor.ScrollType.CENTER
                        )
                    }
                }
            }
        }
    }
}