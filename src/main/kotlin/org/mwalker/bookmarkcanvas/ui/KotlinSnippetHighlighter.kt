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

class KotlinSnippetHighlighter(private val project: Project) : JBPanel<KotlinSnippetHighlighter>() {
    private var mainPanel: JPanel? = null
    private var resultArea: JTextArea? = null

    init {
        initialize()

        // Example Kotlin code with a block comment
        val fullCode = """fun main() {
    println("Hello, World!")

    /* This is a multi-line
    block comment that spans
    several lines of code */

    val x = 42
    println("The answer is ${"$"}x")
}"""

        // Create a range that only includes the end of the comment and some code after it
        val startOffset = fullCode.indexOf("several lines of code")
        val endOffset = fullCode.indexOf("println(\"The answer is")

        // Display the highlighted snippet
        displayHighlightedSnippet(fullCode, TextRange(startOffset, endOffset))
    }

    private fun initialize() {
        mainPanel = JPanel(BorderLayout())
        resultArea = JTextArea()
        resultArea!!.isEditable = false

        mainPanel!!.add(JBScrollPane(resultArea), BorderLayout.CENTER)
        add(mainPanel)
    }

    fun displayHighlightedSnippet(fullKotlinCode: String?, snippetRange: TextRange) {
        // Use a throwaway editor to get proper highlighting
        val editorFactory = EditorFactory.getInstance()
        val document = editorFactory.createDocument(fullKotlinCode!!)

        // Get the current editor color scheme
        val colorsScheme = EditorColorsManager.getInstance().globalScheme

        val editor = editorFactory.createEditor(document, project) as EditorEx

        try {
            // Apply the current color scheme
            editor.colorsScheme = colorsScheme

            // Set up the editor for Kotlin
            editor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(
                project, "file.kt"
            )

            // Get the actual highlighted text
            val segments = extractHighlightedSegments(
                editor, snippetRange
            )

            // Create a component to display the highlighted snippet
            val snippetComponent = createSnippetComponent(segments, colorsScheme)

            // Replace the text area with the highlighting component
            mainPanel!!.removeAll()
            mainPanel!!.add(snippetComponent, BorderLayout.CENTER)
            mainPanel!!.revalidate()
            mainPanel!!.repaint()
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
    ): JComponent {
        // Create a custom component to render the highlighted text with the scheme
        return HighlightedTextComponent(segments, colorsScheme)
    }

    // Class to store text with its highlighting attributes
    private class HighlightedTextSegment(val text: String, val attributes: TextAttributes?)

    // Component that renders the highlighted text segments
    private class HighlightedTextComponent(
        private val segments: List<HighlightedTextSegment>,
        private val colorsScheme: EditorColorsScheme
    ) : JPanel() {
        private val editorFont: Font

        init {
            // Use the editor's font from the color scheme
            val fontName = colorsScheme.editorFontName
            val fontSize = colorsScheme.editorFontSize
            editorFont = Font(fontName, Font.PLAIN, fontSize)

            // Use the editor's background color
            background = colorsScheme.defaultBackground
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g as Graphics2D
            g2d.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            )

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

                    // Set font style
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
                val lines = segment.text.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
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
            // Calculate based on content
            val width = 500 // Default width
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
    }
}