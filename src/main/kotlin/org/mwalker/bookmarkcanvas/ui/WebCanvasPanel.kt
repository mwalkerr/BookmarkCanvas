package org.mwalker.bookmarkcanvas.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.model.CanvasState
import org.mwalker.bookmarkcanvas.model.NodeConnection
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import java.awt.BorderLayout
import java.awt.Point
import java.io.File
import java.nio.file.Paths
import javax.swing.JPanel
import javax.swing.SwingUtilities
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.ui.jcef.*

/**
 * Web-based canvas panel using JCEF WebView
 */
class WebCanvasPanel(val project: Project) : JPanel(BorderLayout()), CanvasInterface {
    private val browser: JBCefBrowser
    private val canvasState: CanvasState = CanvasPersistenceService.getInstance().getCanvasState(project)
    private val gson = Gson()
    
    // Bridge queries for communication with JavaScript
    private val addBookmarkQuery: JBCefJSQuery
    private val clearCanvasQuery: JBCefJSQuery
    private val refreshBookmarksQuery: JBCefJSQuery
    private val createConnectionQuery: JBCefJSQuery
    private val editNodeQuery: JBCefJSQuery
    private val deleteNodeQuery: JBCefJSQuery
    private val copyNodeQuery: JBCefJSQuery
    private val disconnectNodeQuery: JBCefJSQuery
    private val pasteQuery: JBCefJSQuery
    private val nodePositionsChangedQuery: JBCefJSQuery
    private val zoomChangedQuery: JBCefJSQuery
    private val undoQuery: JBCefJSQuery
    private val redoQuery: JBCefJSQuery
    
    companion object {
        private val LOG = Logger.getInstance(WebCanvasPanel::class.java)
    }

    init {
//        LOG.info("JBCefApp.isSupported()?: ${JBCefApp.isSupported()}")
        browser = JBCefBrowser()
        // give the browser an obvious border and background to make sure it's rendering properly
        browser.component.border = javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10)
        browser.component.background = java.awt.Color.BLUE

        // Set up JS queries for communication
        addBookmarkQuery = setupAddBookmarkQuery()
        clearCanvasQuery = setupClearCanvasQuery()
        refreshBookmarksQuery = setupRefreshBookmarksQuery()
        createConnectionQuery = setupCreateConnectionQuery()
        editNodeQuery = setupEditNodeQuery()
        deleteNodeQuery = setupDeleteNodeQuery()
        copyNodeQuery = setupCopyNodeQuery()
        disconnectNodeQuery = setupDisconnectNodeQuery()
        pasteQuery = setupPasteQuery()
        nodePositionsChangedQuery = setupNodePositionsChangedQuery()
        zoomChangedQuery = setupZoomChangedQuery()
        undoQuery = setupUndoQuery()
        redoQuery = setupRedoQuery()
        
        // Set up load handler to inject bridge functions
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    setupJavaScriptBridge()
                    loadCanvasState()
                }
            }
        }, browser.cefBrowser)
        
        // Load the HTML file
        loadCanvasHtml()
        
        add(browser.component, BorderLayout.CENTER)
    }
    
    private fun loadCanvasHtml() {
        try {
            LOG.info("Loading canvas HTML file")
            
            // Load the actual canvas HTML by reading it from resources and using loadHTML
            val canvasHtml = loadCanvasHtmlFromResources()
            if (canvasHtml != null) {
                browser.loadHTML(canvasHtml)
                LOG.info("Canvas HTML loaded via loadHTML")
            } else {
                LOG.error("Could not load canvas HTML from resources")
                // Fallback to test HTML
                loadTestHtml()
            }
        } catch (e: Exception) {
            LOG.error("Error loading canvas HTML", e)
            // Fallback to test HTML
            loadTestHtml()
        }
    }
    
    private fun loadCanvasHtmlFromResources(): String? {
        return try {
            // First try to load from resources
            val htmlStream = this::class.java.getResourceAsStream("/web/canvas.html")
            if (htmlStream != null) {
                val htmlContent = htmlStream.bufferedReader().use { it.readText() }
                
                // Also load CSS and JS content and embed them inline
                val cssContent = this::class.java.getResourceAsStream("/web/canvas.css")?.bufferedReader()?.use { it.readText() }
                val jsContent = this::class.java.getResourceAsStream("/web/canvas.js")?.bufferedReader()?.use { it.readText() }
                
                // Replace external references with inline content
                var modifiedHtml = htmlContent
                if (cssContent != null) {
                    modifiedHtml = modifiedHtml.replace(
                        """<link rel="stylesheet" href="canvas.css">""",
                        "<style>\n$cssContent\n</style>"
                    )
                }
                if (jsContent != null) {
                    modifiedHtml = modifiedHtml.replace(
                        """<script src="canvas.js"></script>""",
                        "<script>\n$jsContent\n</script>"
                    )
                }
                
                LOG.info("Successfully loaded and processed canvas HTML from resources")
                modifiedHtml
            } else {
                // Try loading from file system for development
                val resourcesPath = Paths.get("src/main/resources/web/canvas.html")
                if (resourcesPath.toFile().exists()) {
                    val htmlContent = resourcesPath.toFile().readText()
                    val cssPath = Paths.get("src/main/resources/web/canvas.css")
                    val jsPath = Paths.get("src/main/resources/web/canvas.js")
                    
                    var modifiedHtml = htmlContent
                    if (cssPath.toFile().exists()) {
                        val cssContent = cssPath.toFile().readText()
                        modifiedHtml = modifiedHtml.replace(
                            """<link rel="stylesheet" href="canvas.css">""",
                            "<style>\n$cssContent\n</style>"
                        )
                    }
                    if (jsPath.toFile().exists()) {
                        val jsContent = jsPath.toFile().readText()
                        modifiedHtml = modifiedHtml.replace(
                            """<script src="canvas.js"></script>""",
                            "<script>\n$jsContent\n</script>"
                        )
                    }
                    
                    LOG.info("Successfully loaded and processed canvas HTML from file system")
                    modifiedHtml
                } else {
                    LOG.error("Could not find canvas.html in resources or file system")
                    null
                }
            }
        } catch (e: Exception) {
            LOG.error("Error reading canvas HTML", e)
            null
        }
    }
    
    private fun loadTestHtml() {
        val testHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>BookmarkCanvas WebView Test</title>
                <style>
                    body { 
                        background-color: #1e1e1e; 
                        color: #ffffff; 
                        font-size: 18px; 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        text-align: center;
                        margin: 20px;
                    }
                    .working { color: #4CAF50; }
                    .error { color: #f44336; }
                    .status { margin: 10px 0; padding: 10px; border: 1px solid #333; border-radius: 4px; }
                </style>
            </head>
            <body>
                <h1>BookmarkCanvas WebView - Fallback</h1>
                <div class="status error">Canvas HTML failed to load</div>
                <div class="status working">✓ JCEF Browser is working!</div>
                <div class="status working">✓ HTML rendering is functional</div>
                <div class="status working">✓ CSS styling is applied</div>
                <div id="js-test" class="status error">✗ JavaScript test pending...</div>
                
                <script>
                    console.log('JavaScript is executing in BookmarkCanvas WebView');
                    document.getElementById('js-test').innerHTML = '✓ JavaScript is working!';
                    document.getElementById('js-test').className = 'status working';
                </script>
            </body>
            </html>
        """.trimIndent()
        
        browser.loadHTML(testHtml)
        LOG.info("Loaded fallback test HTML")
    }
    
    private fun setupJavaScriptBridge() {
        val bridgeScript = """
            window.kotlinBridge = {
                addBookmark: function(x, y) {
                    ${addBookmarkQuery.inject("JSON.stringify({x: x, y: y})")}
                },
                clearCanvas: function() {
                    ${clearCanvasQuery.inject("'clear'")}
                },
                refreshBookmarks: function() {
                    ${refreshBookmarksQuery.inject("'refresh'")}
                },
                createConnection: function(sourceId, targetId) {
                    ${createConnectionQuery.inject("JSON.stringify({sourceId: sourceId, targetId: targetId})")}
                },
                editNode: function(nodeId) {
                    ${editNodeQuery.inject("nodeId")}
                },
                deleteNode: function(nodeId) {
                    ${deleteNodeQuery.inject("nodeId")}
                },
                copyNode: function(nodeId) {
                    ${copyNodeQuery.inject("nodeId")}
                },
                disconnectNode: function(nodeId) {
                    ${disconnectNodeQuery.inject("nodeId")}
                },
                paste: function(x, y) {
                    ${pasteQuery.inject("JSON.stringify({x: x, y: y})")}
                },
                onNodePositionsChanged: function(positions) {
                    ${nodePositionsChangedQuery.inject("JSON.stringify(positions)")}
                },
                onZoomChanged: function(zoom) {
                    ${zoomChangedQuery.inject("zoom.toString()")}
                },
                undo: function() {
                    ${undoQuery.inject("'undo'")}
                },
                redo: function() {
                    ${redoQuery.inject("'redo'")}
                },
                onCanvasReady: function() {
                    console.log('Canvas is ready');
                }
            };
        """.trimIndent()
        
        browser.cefBrowser.executeJavaScript(bridgeScript, "", 0)
    }

    private fun setupAddBookmarkQuery(): JBCefJSQuery {
        return JBCefJSQuery.create(browser as JBCefBrowserBase).also { query ->
            query.addHandler { request ->
                SwingUtilities.invokeLater {
                    try {
                        val point = gson.fromJson(request, Point::class.java)
                        showAddBookmarkDialog(point)
                    } catch (e: Exception) {
                        LOG.error("Error handling add bookmark request", e)
                    }
                }
                null
            }
        }
    }

    private fun setupClearCanvasQuery(): JBCefJSQuery {
        return JBCefJSQuery.create(browser as JBCefBrowser).also { query ->
            query.addHandler { _ ->
                SwingUtilities.invokeLater {
                    clearCanvas()
                }
                null
            }
        }
    }
    
    private fun setupRefreshBookmarksQuery(): JBCefJSQuery {
        return JBCefJSQuery.create(browser as JBCefBrowser).also { query ->
            query.addHandler { _ ->
                SwingUtilities.invokeLater {
                    refreshBookmarks()
                }
                null
            }
        }
    }
    
    private fun setupCreateConnectionQuery(): JBCefJSQuery {
        return JBCefJSQuery.create(browser as JBCefBrowser).also { query ->
            query.addHandler { request ->
                SwingUtilities.invokeLater {
                    try {
                        val connectionData = gson.fromJson(request, ConnectionRequest::class.java)
                        createConnection(connectionData.sourceId, connectionData.targetId)
                    } catch (e: Exception) {
                        LOG.error("Error handling create connection request", e)
                    }
                }
                null
            }
        }
    }
    
    private fun setupEditNodeQuery(): JBCefJSQuery {
        return JBCefJSQuery.create(browser as JBCefBrowser).also { query ->
            query.addHandler { nodeId ->
                SwingUtilities.invokeLater {
                    editNode(nodeId)
                }
                null
            }
        }
    }
    
    private fun setupDeleteNodeQuery(): JBCefJSQuery {
        return JBCefJSQuery.create(browser as JBCefBrowser).also { query ->
            query.addHandler { nodeId ->
                SwingUtilities.invokeLater {
                    deleteNode(nodeId)
                }
                null
            }
        }
    }
    
    private fun setupCopyNodeQuery(): JBCefJSQuery {
        return JBCefJSQuery.create(browser as JBCefBrowser).also { query ->
            query.addHandler { nodeId ->
                SwingUtilities.invokeLater {
                    copyNode(nodeId)
                }
                null
            }
        }
    }
    
    private fun setupDisconnectNodeQuery(): JBCefJSQuery {
        return JBCefJSQuery.create(browser as JBCefBrowser).also { query ->
            query.addHandler { nodeId ->
                SwingUtilities.invokeLater {
                    disconnectNode(nodeId)
                }
                null
            }
        }
    }
    
    private fun setupPasteQuery(): JBCefJSQuery {
        return JBCefJSQuery.create(browser as JBCefBrowser).also { query ->
            query.addHandler { request ->
                SwingUtilities.invokeLater {
                    try {
                        val point = gson.fromJson(request, Point::class.java)
                        pasteAtPoint(point)
                    } catch (e: Exception) {
                        LOG.error("Error handling paste request", e)
                    }
                }
                null
            }
        }
    }
    
    private fun setupNodePositionsChangedQuery(): JBCefJSQuery {
        return JBCefJSQuery.create(browser as JBCefBrowser).also { query ->
            query.addHandler { request ->
                SwingUtilities.invokeLater {
                    try {
                        val type = object : TypeToken<Map<String, NodePosition>>() {}.type
                        val positions: Map<String, NodePosition> = gson.fromJson(request, type)
                        updateNodePositions(positions)
                    } catch (e: Exception) {
                        LOG.error("Error handling node positions changed", e)
                    }
                }
                null
            }
        }
    }
    
    private fun setupZoomChangedQuery(): JBCefJSQuery {
        return JBCefJSQuery.create(browser as JBCefBrowser).also { query ->
            query.addHandler { zoomStr ->
                SwingUtilities.invokeLater {
                    try {
                        val zoom = zoomStr.toDouble()
                        canvasState.zoomFactor = zoom
                        saveState()
                    } catch (e: Exception) {
                        LOG.error("Error handling zoom changed", e)
                    }
                }
                null
            }
        }
    }
    
    private fun setupUndoQuery(): JBCefJSQuery {
        return JBCefJSQuery.create(browser as JBCefBrowser).also { query ->
            query.addHandler { _ ->
                SwingUtilities.invokeLater {
                    undo()
                }
                null
            }
        }
    }
    
    private fun setupRedoQuery(): JBCefJSQuery {
        return JBCefJSQuery.create(browser as JBCefBrowser).also { query ->
            query.addHandler { _ ->
                SwingUtilities.invokeLater {
                    redo()
                }
                null
            }
        }
    }
    
    // Canvas operations
    override fun addNodeComponent(node: BookmarkNode) {
        canvasState.addNode(node)
        saveState()
        
        val nodeJson = gson.toJson(node)
        browser.cefBrowser.executeJavaScript(
            "if (window.canvasBridge) { window.canvasBridge.addNode($nodeJson); }",
            "", 0
        )
    }
    
    fun removeNodeComponent(nodeId: String) {
        canvasState.removeNode(nodeId)
        saveState()
        
        browser.cefBrowser.executeJavaScript(
            "if (window.canvasBridge) { window.canvasBridge.removeNode('$nodeId'); }",
            "", 0
        )
    }
    
    override fun clearCanvas() {
        canvasState.nodes.clear()
        canvasState.connections.clear()
        saveState()
        
        browser.cefBrowser.executeJavaScript(
            "if (window.canvasBridge) { window.canvasBridge.clearCanvas(); }",
            "", 0
        )
    }
    
    override fun refreshFromState() {
        loadCanvasState()
    }
    
    private fun loadCanvasState() {
        val stateJson = gson.toJson(CanvasStateForWeb(
            nodes = canvasState.nodes.values.toList(),
            connections = canvasState.connections,
            zoomFactor = canvasState.zoomFactor,
            scrollPositionX = canvasState.scrollPositionX,
            scrollPositionY = canvasState.scrollPositionY,
            showGrid = canvasState.showGrid,
            snapToGrid = canvasState.snapToGrid
        ))
        
        browser.cefBrowser.executeJavaScript(
            "if (window.canvasBridge) { window.canvasBridge.refreshFromState($stateJson); }",
            "", 0
        )
    }
    
    override fun setSnapToGrid(value: Boolean) {
        canvasState.setGridPreferences(value, canvasState.showGrid)
        saveState()
        
        browser.cefBrowser.executeJavaScript(
            "if (window.canvasBridge) { window.canvasBridge.setGridSettings(${canvasState.showGrid}, ${canvasState.snapToGrid}); }",
            "", 0
        )
    }
    
    override fun setShowGrid(value: Boolean) {
        canvasState.setGridPreferences(canvasState.snapToGrid, value)
        saveState()
        
        browser.cefBrowser.executeJavaScript(
            "if (window.canvasBridge) { window.canvasBridge.setGridSettings(${canvasState.showGrid}, ${canvasState.snapToGrid}); }",
            "", 0
        )
    }
    
    override fun zoomIn() {
        browser.cefBrowser.executeJavaScript(
            "if (window.canvasBridge) { window.canvasBridge.zoomIn(); }",
            "", 0
        )
    }
    
    override fun zoomOut() {
        browser.cefBrowser.executeJavaScript(
            "if (window.canvasBridge) { window.canvasBridge.zoomOut(); }",
            "", 0
        )
    }
    
    override fun goHome() {
        browser.cefBrowser.executeJavaScript(
            "if (window.canvasBridge) { window.canvasBridge.goHome(); }",
            "", 0
        )
    }
    
    override fun undo() {
        if (canvasState.undo()) {
            refreshFromState()
        }
    }
    
    override fun redo() {
        if (canvasState.redo()) {
            refreshFromState()
        }
    }
    
    // Placeholder implementations for action handlers
    private fun showAddBookmarkDialog(point: Point) {
        // TODO: Implement bookmark dialog
        LOG.info("Add bookmark at point: $point")
    }
    
    private fun refreshBookmarks() {
        // TODO: Implement bookmark refresh
        LOG.info("Refresh bookmarks")
    }
    
    private fun createConnection(sourceId: String, targetId: String) {
        val sourceNode = canvasState.nodes[sourceId]
        val targetNode = canvasState.nodes[targetId]
        
        if (sourceNode != null && targetNode != null) {
            val connection = NodeConnection(
                sourceNodeId = sourceId,
                targetNodeId = targetId
            )
            
            canvasState.addConnection(connection)
            saveState()
            
            val connectionJson = gson.toJson(connection)
            browser.cefBrowser.executeJavaScript(
                "if (window.canvasBridge) { window.canvasBridge.addConnection($connectionJson); }",
                "", 0
            )
        }
    }
    
    private fun editNode(nodeId: String) {
        // TODO: Implement node editing
        LOG.info("Edit node: $nodeId")
    }
    
    private fun deleteNode(nodeId: String) {
        removeNodeComponent(nodeId)
    }
    
    private fun copyNode(nodeId: String) {
        // TODO: Implement node copying
        LOG.info("Copy node: $nodeId")
    }
    
    private fun disconnectNode(nodeId: String) {
        canvasState.connections.removeIf { it.sourceNodeId == nodeId || it.targetNodeId == nodeId }
        saveState()
        refreshFromState()
    }
    
    private fun pasteAtPoint(point: Point) {
        // TODO: Implement paste functionality
        LOG.info("Paste at point: $point")
    }
    
    private fun updateNodePositions(positions: Map<String, NodePosition>) {
        positions.forEach { (nodeId, position) ->
            canvasState.updateNodePosition(nodeId, position.x, position.y)
        }
        saveState()
    }
    
    private fun saveState() {
        CanvasPersistenceService.getInstance().saveCanvasState(project, canvasState)
    }
    
    // Helper classes for JSON serialization
    private data class ConnectionRequest(val sourceId: String, val targetId: String)
    private data class NodePosition(val x: Int, val y: Int)
    private data class CanvasStateForWeb(
        val nodes: List<BookmarkNode>,
        val connections: List<NodeConnection>,
        val zoomFactor: Double,
        val scrollPositionX: Int,
        val scrollPositionY: Int,
        val showGrid: Boolean,
        val snapToGrid: Boolean
    )
}