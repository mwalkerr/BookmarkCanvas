package org.mwalker.bookmarkcanvas.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import org.mwalker.bookmarkcanvas.model.BookmarkNode
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

/**
 * Dialog for editing the source information of a BookmarkNode
 */
class EditSourceDialog(
    private val project: Project,
    private val node: BookmarkNode
) : DialogWrapper(project) {
    
    private val filePathField = TextFieldWithBrowseButton().apply {
        text = node.filePath
        addBrowseFolderListener(
            "Select File",
            "Choose the source file",
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )
    }
    private val lineNumberField = JTextField((node.lineNumber0Based + 1).toString(), 10)
    private val contextBeforeField = JTextField(node.contextLinesBefore.toString(), 5)
    private val contextAfterField = JTextField(node.contextLinesAfter.toString(), 5)
    
    init {
        title = "Edit Source Information"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        
        // File path
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = Insets(5, 5, 5, 5)
        panel.add(JLabel("File Path:"), gbc)
        
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(filePathField, gbc)
        
        // Line number
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        panel.add(JLabel("Line Number:"), gbc)
        
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(lineNumberField, gbc)
        
        // Context lines before
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        panel.add(JLabel("Context Lines Before:"), gbc)
        
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(contextBeforeField, gbc)
        
        // Context lines after
        gbc.gridx = 0
        gbc.gridy = 3
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        panel.add(JLabel("Context Lines After:"), gbc)
        
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(contextAfterField, gbc)
        
        return panel
    }
    
    override fun doOKAction() {
        if (validateInput()) {
            applyChanges()
            super.doOKAction()
        }
    }
    
    private fun validateInput(): Boolean {
        // Validate line number
        try {
            val lineNumber = lineNumberField.text.toInt()
            if (lineNumber < 1) {
                JOptionPane.showMessageDialog(
                    contentPanel,
                    "Line number must be 1 or greater",
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE
                )
                return false
            }
        } catch (e: NumberFormatException) {
            JOptionPane.showMessageDialog(
                contentPanel,
                "Line number must be a valid number",
                "Invalid Input",
                JOptionPane.ERROR_MESSAGE
            )
            return false
        }
        
        // Validate context lines
        try {
            val contextBefore = contextBeforeField.text.toInt()
            val contextAfter = contextAfterField.text.toInt()
            if (contextBefore < 0 || contextAfter < 0) {
                JOptionPane.showMessageDialog(
                    contentPanel,
                    "Context lines must be 0 or greater",
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE
                )
                return false
            }
        } catch (e: NumberFormatException) {
            JOptionPane.showMessageDialog(
                contentPanel,
                "Context lines must be valid numbers",
                "Invalid Input",
                JOptionPane.ERROR_MESSAGE
            )
            return false
        }
        
        // Validate file path (basic check)
        if (filePathField.text.isBlank()) {
            JOptionPane.showMessageDialog(
                contentPanel,
                "File path cannot be empty",
                "Invalid Input",
                JOptionPane.ERROR_MESSAGE
            )
            return false
        }
        
        return true
    }
    
    private fun applyChanges() {
        node.filePath = filePathField.text.trim()
        node.lineNumber0Based = lineNumberField.text.toInt() - 1
        node.contextLinesBefore = contextBeforeField.text.toInt()
        node.contextLinesAfter = contextAfterField.text.toInt()
        
        // Refresh content to reflect any changes
        node.refreshContent(project)
    }
}