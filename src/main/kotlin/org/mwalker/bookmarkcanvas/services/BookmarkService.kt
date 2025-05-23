package org.mwalker.bookmarkcanvas.services

import org.mwalker.bookmarkcanvas.model.BookmarkNode
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

class BookmarkService {
    companion object {
        private val LOG = Logger.getInstance(BookmarkService::class.java)
        
        /**
         * Resolves path macros in file paths and makes the path relative to the project when possible.
         * Handles system path variables like USER_HOME.
         */
        private fun resolveFilePath(project: Project, originalPath: String): String {
            LOG.info("Original path: $originalPath, resolving with PathMacroManager")
            // Use PathMacroManager to resolve path macros
            var filePath = com.intellij.openapi.util.io.FileUtil.toSystemDependentName(originalPath)
            
            // Try to resolve macros with PathMacroManager
            val pathMacroManager = com.intellij.openapi.components.PathMacroManager.getInstance(project)
            filePath = pathMacroManager.expandPath(filePath)
            
            // Make path relative to project if possible
            if (project.basePath != null && filePath.startsWith(project.basePath!!)) {
                filePath = filePath.replace(project.basePath!! + "/", "")
            }
            LOG.info("Resolved path: $filePath")
            return filePath
        }
        fun createNodeFromCurrentPosition(project: Project): BookmarkNode? {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null

            val document = editor.document
            val file = FileDocumentManager.getInstance().getFile(document) ?: return null
            
            // Resolve the file path
            val filePath = resolveFilePath(project, file.path)
            
            // Check if there's a text selection
            val selectionModel = editor.selectionModel
            if (selectionModel.hasSelection()) {
                // Get the selection bounds
                val selectionStart = selectionModel.selectionStart
                val selectionEnd = selectionModel.selectionEnd
                
                // Get the line numbers where the selection starts and ends
                val startLine = document.getLineNumber(selectionStart)
                val endLine = document.getLineNumber(selectionEnd)
                val linesInSelection = endLine - startLine + 1
                
                // Store the first line content for navigation purposes
                val firstLineStart = document.getLineStartOffset(startLine)
                val firstLineEnd = document.getLineEndOffset(startLine)
                val firstLineContent = document.getText(com.intellij.openapi.util.TextRange(firstLineStart, firstLineEnd))
                
                // Create a node with the selection information
                return BookmarkNode(
                    bookmarkId = "bookmark_" + System.currentTimeMillis(),
                    filePath = filePath,
                    lineNumber0Based = startLine,
                    lineContent = firstLineContent,
                    showCodeSnippet = true,
                    contextLinesBefore = 0,
                    contextLinesAfter = linesInSelection - 1
                )
            } else {
                // Regular bookmark - just use the current line
                val line = editor.caretModel.logicalPosition.line
                
                // Get the line content
                val lineContent = if (line >= 0 && line < document.lineCount) {
                    val lineStart = document.getLineStartOffset(line)
                    val lineEnd = document.getLineEndOffset(line)
                    document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
                } else null

                return BookmarkNode(
                    bookmarkId = "bookmark_" + System.currentTimeMillis(),
                    filePath = filePath,
                    lineNumber0Based = line,
                    lineContent = lineContent
                )
            }
        }

        fun createFileNodeFromCurrentFile(project: Project): BookmarkNode? {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            val file = if (editor != null) {
                FileDocumentManager.getInstance().getFile(editor.document)
            } else {
                // Try to get the currently selected file in the project view
                val fileEditorManager = FileEditorManager.getInstance(project)
                fileEditorManager.selectedFiles.firstOrNull()
            } ?: return null
            
            // Resolve the file path
            val filePath = resolveFilePath(project, file.path)
            
            return BookmarkNode(
                bookmarkId = "file_" + System.currentTimeMillis(),
                filePath = filePath,
                lineNumber0Based = 0,
                lineContent = "File: ${file.name}"
            )
        }

        fun getAllBookmarkNodes(project: Project): List<BookmarkNode> {
            val result = mutableListOf<BookmarkNode>()

            val bookmarkManager = BookmarksManager.getInstance(project)
            val bookmarks = bookmarkManager?.bookmarks?.mapNotNull { it as? LineBookmark
            } ?: return listOf()

            for (bookmark in bookmarks) {
                LOG.info("Bookmark: $bookmark")
                LOG.info("Bookmark attributes: ${bookmark.attributes}")
                // Convert IDE bookmarks to our model using our path resolution method
                val filePath = resolveFilePath(project, bookmark.file.path)

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
//                    displayName = bookmark.description,
//                    displayName = (bookmark.file.name + ":" + bookmark.line), // Or file:line
                    filePath = filePath,
                    lineNumber0Based = bookmark.line,
                    lineContent = lineContent
                )

                result.add(node)
            }

            return result
        }
    }
}