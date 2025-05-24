package org.mwalker.bookmarkcanvas.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import javax.swing.SwingConstants

class WebViewCanvasToolWindow : ToolWindowFactory {
    override fun createToolWindowContent(@NotNull project: Project, @NotNull toolWindow: ToolWindow) {
        val contentComponent = try {
            // Check if JCEF is available before creating the web canvas
            Class.forName("com.intellij.ui.jcef.JBCefApp")
            val jcefAppClass = Class.forName("com.intellij.ui.jcef.JBCefApp")
            val isSupportedMethod = jcefAppClass.getMethod("isSupported")
            val isSupported = isSupportedMethod.invoke(null) as Boolean
            
            if (isSupported) {
                // Create the web-based canvas toolbar
                WebCanvasToolbar(project)
            } else {
                createFallbackPanel("JCEF is not supported in this IntelliJ installation")
            }
        } catch (e: ClassNotFoundException) {
            createFallbackPanel("JCEF is not available in this IntelliJ installation")
        } catch (e: Exception) {
            createFallbackPanel("Error initializing web canvas: ${e.message}")
        }

        val content = ContentFactory.getInstance().createContent(
            contentComponent, "", false)
        toolWindow.contentManager.addContent(content)
    }
    
    private fun createFallbackPanel(message: String): JBPanel<*> {
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        val label = JBLabel("<html><center><h3>Web Canvas Unavailable</h3><p>$message</p><p>Please use the regular Bookmark Canvas instead.</p></center></html>")
        label.horizontalAlignment = SwingConstants.CENTER
        panel.add(label, BorderLayout.CENTER)
        return panel
    }
}