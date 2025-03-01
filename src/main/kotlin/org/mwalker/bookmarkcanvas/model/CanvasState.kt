package org.mwalker.bookmarkcanvas.model

class CanvasState {
    val nodes = mutableMapOf<String, org.mwalker.bookmarkcanvas.model.BookmarkNode>()
    val connections = mutableListOf<org.mwalker.bookmarkcanvas.model.NodeConnection>()

    fun addNode(node: org.mwalker.bookmarkcanvas.model.BookmarkNode) {
        nodes[node.id] = node
    }

    fun removeNode(nodeId: String) {
        nodes.remove(nodeId)
        // Also remove any connections involving this node
        connections.removeIf { it.sourceNodeId == nodeId || it.targetNodeId == nodeId }
    }

    fun addConnection(connection: org.mwalker.bookmarkcanvas.model.NodeConnection) {
        connections.add(connection)
    }

    fun removeConnection(connectionId: String) {
        connections.removeIf { it.id == connectionId }
    }
}