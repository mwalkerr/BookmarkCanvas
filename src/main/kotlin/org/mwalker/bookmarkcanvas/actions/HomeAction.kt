package org.mwalker.bookmarkcanvas.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.mwalker.bookmarkcanvas.ui.CanvasPanel
import javax.swing.Icon
import com.intellij.openapi.util.IconLoader

class HomeAction(private val canvasPanel: CanvasPanel) : AnAction("Go to Home Node", "Go to the top-left node in the canvas", AllIcons.Nodes.HomeFolder) {

    override fun actionPerformed(e: AnActionEvent) {
        // Find the top-left most node and recenter on it
        canvasPanel.goToTopLeftNode()
    }

    override fun update(e: AnActionEvent) {
        // Enable the action only if we have nodes
        e.presentation.isEnabled = canvasPanel.canvasState.nodes.isNotEmpty()
    }
}