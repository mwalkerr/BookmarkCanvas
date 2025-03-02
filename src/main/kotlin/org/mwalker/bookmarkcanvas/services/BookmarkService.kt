package org.mwalker.bookmarkcanvas.services

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.FileBookmark
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class BookmarkService {
    companion object {
        fun createNodeFromCurrentPosition(project: Project): BookmarkNode? {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null

            val document = editor.document
            val file = FileDocumentManager.getInstance().getFile(document) ?: return null

            val line = editor.caretModel.logicalPosition.line
            val filePath = file.path.replace(project.basePath + "/", "")
            
            // Get the line content
            val lineContent = if (line >= 0 && line < document.lineCount) {
                val lineStart = document.getLineStartOffset(line)
                val lineEnd = document.getLineEndOffset(line)
                document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
            } else null

            return BookmarkNode(
                bookmarkId = "bookmark_" + System.currentTimeMillis(),
                displayName = file.name + ":" + (line + 1),
                filePath = filePath,
                lineNumber = line,
                lineContent = lineContent
            )
        }

        fun getAllBookmarkNodes(project: Project): List<BookmarkNode> {
            val result = mutableListOf<BookmarkNode>()

            val bookmarkManager = BookmarksManager.getInstance(project)
            val bookmarks = bookmarkManager?.bookmarks?.mapNotNull { it as? LineBookmark
            } ?: return listOf()

            for (bookmark in bookmarks) {
                // Convert IDE bookmarks to our model
                // This is a simplified version - you'll need to adapt to the actual IDE API
                val filePath = bookmark.file.path.replace(project.basePath + "/", "")

                // Get the line content
                val lineContent = try {
                    val file = bookmark.file
                    val document = FileDocumentManager.getInstance().getDocument(file)
                    if (document != null && bookmark.line >= 0 && bookmark.line < document.lineCount) {
                        val lineStart = document.getLineStartOffset(bookmark.line)
                        val lineEnd = document.getLineEndOffset(bookmark.line)
                        document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
                    } else null
                } catch (e: Exception) {
                    null
                }
                
                val node = BookmarkNode(
                    bookmarkId = bookmark.toString(), // Or a unique ID
//                    displayName = bookmark.description ?: (bookmark.file.name + ":" + bookmark.line), // Or file:line
                    displayName = (bookmark.file.name + ":" + bookmark.line), // Or file:line
                    filePath = filePath,
                    lineNumber = bookmark.line,
                    lineContent = lineContent
                )

                result.add(node)
            }

            return result
        }
    }
}