package org.mwalker.bookmarkcanvas.model
import com.intellij.openapi.diagnostic.Logger


class CanvasState {
    companion object {
        private val LOG = Logger.getInstance(CanvasState::class.java)
    }
    val nodes = mutableMapOf<String, org.mwalker.bookmarkcanvas.model.BookmarkNode>()
    val connections = mutableListOf<org.mwalker.bookmarkcanvas.model.NodeConnection>()
    
    // Grid preferences
    var snapToGrid: Boolean = false
    var showGrid: Boolean = false
    
    // Canvas view state
    var zoomFactor: Double = 1.0
    var scrollPositionX: Int = 0
    var scrollPositionY: Int = 0
    
    // History for undo functionality
    private val history = mutableListOf<CanvasStateSnapshot>()
    private var historyIndex = -1
    private var isUndoRedo = false
    
    init {
        // Save initial state
        saveSnapshot()
    }

    fun addNode(node: org.mwalker.bookmarkcanvas.model.BookmarkNode) {
        LOG.info("Adding node: $node")
        if (!isUndoRedo) saveSnapshot()
        nodes[node.id] = node
    }

    fun removeNode(nodeId: String) {
        LOG.info("Removing node: $nodeId")
        if (!isUndoRedo) saveSnapshot()
        nodes.remove(nodeId)
        // Also remove any connections involving this node
        connections.removeIf { it.sourceNodeId == nodeId || it.targetNodeId == nodeId }
    }

    fun addConnection(connection: org.mwalker.bookmarkcanvas.model.NodeConnection) {
        LOG.info("Adding connection: $connection")
        if (!isUndoRedo) saveSnapshot()
        connections.add(connection)
    }

    fun removeConnection(connectionId: String) {
        LOG.info("Removing connection: $connectionId")
        if (!isUndoRedo) saveSnapshot()
        connections.removeIf { it.id == connectionId }
    }
    
    fun updateNodePosition(nodeId: String, x: Int, y: Int) {
        val node = nodes[nodeId] ?: return
        if (node.positionX == x && node.positionY == y) return
        
        if (!isUndoRedo) saveSnapshot()
        node.positionX = x
        node.positionY = y
    }
    
    fun setGridPreferences(snapToGrid: Boolean, showGrid: Boolean) {
        if (this.snapToGrid == snapToGrid && this.showGrid == showGrid) return
        
        if (!isUndoRedo) saveSnapshot()
        this.snapToGrid = snapToGrid
        this.showGrid = showGrid
    }
    
    // Undo/Redo functionality
    fun canUndo(): Boolean = historyIndex > 0
    
    fun canRedo(): Boolean = historyIndex < history.size - 1
    
    fun undo(): Boolean {
        if (!canUndo()) return false
        
        historyIndex--
        applySnapshot(history[historyIndex])
        return true
    }
    
    fun redo(): Boolean {
        if (!canRedo()) return false
        
        historyIndex++
        applySnapshot(history[historyIndex])
        return true
    }
    
    private fun saveSnapshot() {
        // Remove any redo states if we're making a new change
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        
        // Create a snapshot
        val snapshot = CanvasStateSnapshot(
            nodes = nodes.mapValues { it.value.copy() },
            connections = connections.map { it.copy() },
            snapToGrid = snapToGrid,
            showGrid = showGrid,
            zoomFactor = zoomFactor,
            scrollPositionX = scrollPositionX,
            scrollPositionY = scrollPositionY
        )
        
        // Add to history, limit history size to prevent memory issues
        if (history.size >= 30) {
            history.removeAt(0)
        }
        history.add(snapshot)
        historyIndex = history.size - 1
    }
    
    private fun applySnapshot(snapshot: CanvasStateSnapshot) {
        isUndoRedo = true
        
        // Clear current state
        nodes.clear()
        connections.clear()
        
        // Apply snapshot
        nodes.putAll(snapshot.nodes)
        connections.addAll(snapshot.connections)
        snapToGrid = snapshot.snapToGrid
        showGrid = snapshot.showGrid
        zoomFactor = snapshot.zoomFactor
        scrollPositionX = snapshot.scrollPositionX
        scrollPositionY = snapshot.scrollPositionY
        
        isUndoRedo = false
    }
    
    // Inner class to store snapshots
    private data class CanvasStateSnapshot(
        val nodes: Map<String, BookmarkNode>,
        val connections: List<NodeConnection>,
        val snapToGrid: Boolean,
        val showGrid: Boolean,
        val zoomFactor: Double,
        val scrollPositionX: Int,
        val scrollPositionY: Int
    )
}