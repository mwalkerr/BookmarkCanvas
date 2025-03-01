package org.mwalker.bookmarkcanvas.model
import com.intellij.openapi.diagnostic.Logger


class CanvasState {
    companion object {
        private val LOG = Logger.getInstance(CanvasState::class.java)
    }
    val nodes = mutableMapOf<String, org.mwalker.bookmarkcanvas.model.BookmarkNode>()
    val connections = mutableListOf<org.mwalker.bookmarkcanvas.model.NodeConnection>()

    fun addNode(node: org.mwalker.bookmarkcanvas.model.BookmarkNode) {
        LOG.info("Adding node: $node")
        nodes[node.id] = node
    }

    fun removeNode(nodeId: String) {
        LOG.info("Adding node: $nodeId")
        nodes.remove(nodeId)
        // Also remove any connections involving this node
        connections.removeIf { it.sourceNodeId == nodeId || it.targetNodeId == nodeId }
    }

    fun addConnection(connection: org.mwalker.bookmarkcanvas.model.NodeConnection) {
        LOG.info("Adding connection: $connection")
        connections.add(connection)
    }

    fun removeConnection(connectionId: String) {
        LOG.info("Removing connection: $connectionId")
        connections.removeIf { it.id == connectionId }
    }
}