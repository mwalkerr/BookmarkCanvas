package org.mwalker.bookmarkcanvas.ui

import org.mwalker.bookmarkcanvas.model.BookmarkNode

/**
 * Common interface for both AWT-based and web-based canvas panels
 */
interface CanvasInterface {
    fun addNodeComponent(node: BookmarkNode)
    fun clearCanvas()
    fun refreshFromState()
    fun setSnapToGrid(value: Boolean)
    fun setShowGrid(value: Boolean)
    fun zoomIn()
    fun zoomOut()
    fun goHome()
    fun undo()
    fun redo()
}