package org.mwalker.bookmarkcanvas.actions

import org.mwalker.bookmarkcanvas.ui.CanvasPanel
import com.intellij.execution.configurations.ParameterTargetValuePart
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NotNull
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO


class ExportCanvasAction(
    private val project: Project,
    private val canvasPanel: CanvasPanel
) : AnAction("Export Canvas", "Export canvas as image", AllIcons.ToolbarDecorator.Export) {

    override fun actionPerformed(@NotNull e: AnActionEvent) {
        val descriptor = FileSaverDescriptor(
            "Export Canvas", "Choose where to save the canvas image", "png", "jpg"
        )

        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val wrapper = dialog.save(null as VirtualFile?, "code_canvas.png")

        if (wrapper != null) {
            try {
                // Create image from canvas
                val size = canvasPanel.size
                val image = BufferedImage(
                    size.width, size.height, BufferedImage.TYPE_INT_ARGB
                )
                val g2 = image.createGraphics()
                canvasPanel.paint(g2)
                g2.dispose()

                // Save image
                ImageIO.write(image, "png", wrapper.file)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}