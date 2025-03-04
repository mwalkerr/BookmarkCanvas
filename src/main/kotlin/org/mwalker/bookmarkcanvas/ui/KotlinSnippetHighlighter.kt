package org.mwalker.bookmarkcanvas.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import java.awt.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.math.max
import kotlin.math.min
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import java.awt.BasicStroke
import com.intellij.openapi.diagnostic.Logger


class KotlinSnippetHighlighter(private val project: Project) : JBPanel<KotlinSnippetHighlighter>(BorderLayout()) {
    companion object {
        private val LOG = Logger.getInstance(KotlinSnippetHighlighter::class.java)
    }
    private var mainPanel: JPanel? = null
    private var resultArea: JTextArea? = null
    private var currentZoomFactor: Double = 1.0
    private var snippetComponent: HighlightedTextComponent? = null
    private var currentSegments: List<HighlightedTextSegment>? = null
    private var currentColorsScheme: EditorColorsScheme? = null
    
    /**
     * Updates the selection state of this component
     */
    fun setSelected(selected: Boolean) {
        snippetComponent?.setSelected(selected)
    }
    
    init {
        initialize()
    }

    private fun initialize() {
        mainPanel = JPanel(BorderLayout())
        resultArea = JTextArea()
        resultArea!!.isEditable = false
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        border = javax.swing.BorderFactory.createLineBorder(org.mwalker.bookmarkcanvas.ui.CanvasColors.BORDER_COLOR, 1)

        mainPanel!!.add(JBScrollPane(resultArea), BorderLayout.CENTER)
        add(mainPanel, BorderLayout.CENTER)
    }
    
    /**
     * Displays plain text when syntax highlighting is not available
     */
    fun displayPlainText(text: String) {
        if (resultArea == null) {
            resultArea = JTextArea()
            resultArea!!.isEditable = false
            resultArea!!.font = Font("Monospaced", Font.PLAIN, 12)
            resultArea!!.background = EditorColorsManager.getInstance().globalScheme.defaultBackground
            resultArea!!.foreground = EditorColorsManager.getInstance().globalScheme.defaultForeground
            resultArea!!.lineWrap = true
            resultArea!!.wrapStyleWord = true
        }
        
        resultArea!!.text = text
        
        // Normalize indentation - convert to lines, normalize, then join
        val lines = text.lines()
        // Find minimum indentation in non-empty lines
        val minIndent = lines.filter { it.isNotBlank() }
            .minOfOrNull { line -> line.takeWhile { it.isWhitespace() }.length } ?: 0

        // Normalize indentation if needed
        if (minIndent > 0) {
            val normalizedLines = lines.map { line ->
                if (line.isBlank()) line else {
                    val indent = line.takeWhile { it.isWhitespace() }.length
                    if (indent >= minIndent) line.substring(minIndent) else line
                }
            }
            resultArea!!.text = normalizedLines.joinToString("\n")
        }
        
        // Show the text area instead of any highlight component
        mainPanel?.removeAll()
        mainPanel?.add(JBScrollPane(resultArea), BorderLayout.CENTER)
        mainPanel?.revalidate()
        mainPanel?.repaint()
    }
    
    /**
     * Updates the component to respect the current zoom factor
     */
    fun updateZoomFactor(zoomFactor: Double) {
        currentZoomFactor = zoomFactor
        
        // If we have segments to display, recreate the component with the new zoom
        if (currentSegments != null && currentColorsScheme != null) {
            recreateSnippetComponent()
        }
    }
    
    /**
     * Recreates the snippet component with current zoom factor
     */
    private fun recreateSnippetComponent() {
        currentSegments?.let { segments ->
            currentColorsScheme?.let { scheme ->
                snippetComponent = createSnippetComponent(segments, scheme)
                
                mainPanel?.removeAll()
                mainPanel?.add(snippetComponent, BorderLayout.CENTER)
                mainPanel?.revalidate()
                mainPanel?.repaint()
            }
        }
    }

    fun displayHighlightedSnippet(fullKotlinCode: String?, snippetRange: TextRange, fileExtension: String = "kt") {
        // Use a throwaway editor to get proper highlighting
        val editorFactory = EditorFactory.getInstance()
        val document = editorFactory.createDocument(fullKotlinCode ?: "")

        // Get the current editor color scheme
        val colorsScheme = EditorColorsManager.getInstance().globalScheme
        currentColorsScheme = colorsScheme

        val editor = editorFactory.createEditor(document, project) as EditorEx
        editor.foldingModel.isFoldingEnabled = false

        try {
            // Apply the current color scheme
            editor.colorsScheme = colorsScheme

            // Set up the editor for the appropriate file type 
            editor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(
                project, "file.$fileExtension"
            )
            
            // First get all the segments from the editor for the entire snippetRange
            val allSegments = extractHighlightedSegments(editor, snippetRange)
            
            // Extract the snippet text to get its lines
            val snippetText = document.getText(snippetRange)
            val lines = snippetText.lines()

            // Calculate the minimum indentation level for normalization
            val minIndent = lines
                .filter { it.isNotBlank() }
                .minOfOrNull { line -> line.takeWhile { it.isWhitespace() }.length } ?: 0
                
            // If no indentation to remove, use the original segments
            if (minIndent == 0) {
                currentSegments = allSegments
            } else {
                // We need to normalize the indentation
                // The simplest approach is to get the full text, normalize it, 
                // and then create a new document with normalized text for highlighting
                
                // Create a normalized version of the text
                val normalizedText = lines.joinToString("\n") { line ->
                    if (line.isBlank()) line else {
                        val indent = line.takeWhile { it.isWhitespace() }.length
                        val indentToRemove = minOf(minIndent, indent)
                        if (indentToRemove > 0) line.substring(indentToRemove) else line
                    }
                }
                
                //  Create a new document with normalized text
                val normalizedDocument = editorFactory.createDocument(normalizedText)
                val normalizedEditor = editorFactory.createEditor(normalizedDocument, project) as EditorEx
                
                try {
                    // Apply the same settings to this editor
                    normalizedEditor.colorsScheme = colorsScheme
                    normalizedEditor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(
                        project, "file.$fileExtension"
                    )
                    
                    // Extract segments from the normalized text
                    val segments = extractHighlightedSegments(
                        normalizedEditor, 
                        TextRange(0, normalizedText.length)
                    )
                    
                    currentSegments = segments
                } finally {
                    EditorFactory.getInstance().releaseEditor(normalizedEditor)
                }
            }

            // Create a component to display the highlighted snippet
            snippetComponent = createSnippetComponent(currentSegments!!, colorsScheme)

            // Replace the text area with the highlighting component
            mainPanel!!.removeAll()
            mainPanel!!.add(snippetComponent, BorderLayout.CENTER)
            mainPanel!!.revalidate()
            mainPanel!!.repaint()
        } catch (e: Exception) {
            // Log the error
            println("Error highlighting snippet: ${e.message}")
            e.printStackTrace()
            
            // Fallback to plain text if highlighting fails
            val snippetText = document.getText(snippetRange)
            displayPlainText(snippetText)
        } finally {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    private fun extractHighlightedSegments(editor: Editor, range: TextRange): List<HighlightedTextSegment> {
        val segments: MutableList<HighlightedTextSegment> = ArrayList()
        val highlighter = (editor as EditorEx).highlighter

        // Get iterator for the highlighting info
        val iterator = highlighter.createIterator(range.startOffset)

        // Collect all highlighted segments in the range
        while (!iterator.atEnd() && iterator.start < range.endOffset) {
            val attributes = iterator.textAttributes
            val segmentStart = max(iterator.start.toDouble(), range.startOffset.toDouble()).toInt()
            val segmentEnd = min(iterator.end.toDouble(), range.endOffset.toDouble()).toInt()

            if (segmentStart < segmentEnd) {
                val text = editor.document.getText(TextRange(segmentStart, segmentEnd))
                segments.add(HighlightedTextSegment(text, attributes))
            }

            iterator.advance()
        }

        return segments
    }

    private fun createSnippetComponent(
        segments: List<HighlightedTextSegment>,
        colorsScheme: EditorColorsScheme
    ): HighlightedTextComponent {
        // Create a custom component to render the highlighted text with the scheme
        return HighlightedTextComponent(segments, colorsScheme, currentZoomFactor)
    }

    // Class to store text with its highlighting attributes
    private class HighlightedTextSegment(val text: String, val attributes: TextAttributes?)

    // Component that renders the highlighted text segments
    private class HighlightedTextComponent(
        private val segments: List<HighlightedTextSegment>,
        private val colorsScheme: EditorColorsScheme,
        private val zoomFactor: Double = 1.0
    ) : JPanel() {
        private val editorFont: Font
        private val baseEditorFont: Font
        private var isComponentSelected = false
        
        // Set when the component is selected
        fun setSelected(selected: Boolean) {
            if (isComponentSelected != selected) {
                isComponentSelected = selected
                setBorder(createBorder(selected))
                repaint()
            }
        }
        
        // Create a border based on selection state
        private fun createBorder(selected: Boolean): javax.swing.border.Border {
            return javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(org.mwalker.bookmarkcanvas.ui.CanvasColors.BORDER_COLOR, 1),
                javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5)
            )
        }

        init {
            // Use the editor's font from the color scheme
            val fontName = colorsScheme.editorFontName
            val fontSize = colorsScheme.editorFontSize
            baseEditorFont = Font(fontName, Font.PLAIN, fontSize)
            
            // Apply zoom to font size
            val scaledFontSize = (fontSize * zoomFactor).toInt().coerceAtLeast(6)
            editorFont = Font(fontName, Font.PLAIN, scaledFontSize)

            // Use the editor's background color
            background = colorsScheme.defaultBackground
            isOpaque = true
            
            // Set initial border
            border = createBorder(false)
        }
        
        // Minimum width to ensure visibility
        private val minWidth = 200
        
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g as Graphics2D
            g2d.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            )
            
            // Draw the background
            g2d.color = background
            g2d.fillRect(0, 0, width, height)

            val fm = g2d.getFontMetrics(editorFont)
            val lineHeight = fm.height
            var x = 10
            var y = lineHeight

            // Draw each segment with appropriate styling
            for (segment in segments) {
                // Apply text styling
                if (segment.attributes != null) {
                    // Set background color if defined
                    if (segment.attributes.backgroundColor != null) {
                        g2d.color = segment.attributes.backgroundColor
                        g2d.fillRect(
                            x, y - fm.ascent,
                            fm.stringWidth(segment.text), lineHeight
                        )
                    }

                    // Set foreground color
                    g2d.color = segment.attributes.foregroundColor ?: colorsScheme.defaultForeground

                    // Set font style with zoom applied
                    g2d.font = Font(
                        editorFont.family,
                        segment.attributes.fontType,
                        editorFont.size
                    )
                } else {
                    g2d.color = colorsScheme.defaultForeground
                    g2d.font = editorFont
                }

                // Handle multi-line text
                val lines = segment.text.split("\n".toRegex()).toTypedArray()
                for (i in lines.indices) {
                    if (i > 0) {
                        y += lineHeight
                        x = 10
                    }
                    g2d.drawString(lines[i], x, y)
                    x += fm.stringWidth(lines[i])
                }
            }
        }

        override fun getPreferredSize(): Dimension {
            // Calculate based on content with zoom factor applied
            val width = (500 * zoomFactor).toInt().coerceAtLeast(minWidth) // Default width with zoom
            var height = 0

            val fm = getFontMetrics(editorFont)
            val lineHeight = fm.height
            var lineCount = 1

            // Count lines to determine height
            for (segment in segments) {
                lineCount += segment.text.chars().filter { ch: Int -> ch == '\n'.code }.count().toInt()
            }

            height = lineCount * lineHeight + 20 // Add padding
            return Dimension(width, height)
        }
        
        override fun getMinimumSize(): Dimension {
            return Dimension(minWidth, 100)
        }
    }
}