package org.mwalker.bookmarkcanvas.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.model.CanvasState
import org.mwalker.bookmarkcanvas.model.NodeConnection

/**
 * A state class specifically designed for XML serialization.
 * This is used as an intermediate format for the complex CanvasState.
 */
class PersistentCanvasState {
    var projectId: String = ""
    var nodeMap: MutableMap<String, SerializableNode> = mutableMapOf()
    var connections: MutableList<SerializableConnection> = mutableListOf()
}

/**
 * Serializable version of BookmarkNode
 */
class SerializableNode {
    var id: String = ""
    var bookmarkId: String = ""
    var displayName: String = ""
    var filePath: String = ""
    var lineNumber: Int = 0
    var positionX: Int = 100
    var positionY: Int = 100
    var showCodeSnippet: Boolean = false
    var contextLinesBefore: Int = 3
    var contextLinesAfter: Int = 3
    
    companion object {
        fun fromBookmarkNode(node: BookmarkNode): SerializableNode {
            return SerializableNode().apply {
                id = node.id
                bookmarkId = node.bookmarkId
                displayName = node.displayName
                filePath = node.filePath
                lineNumber = node.lineNumber
                positionX = node.positionX
                positionY = node.positionY
                showCodeSnippet = node.showCodeSnippet
                contextLinesBefore = node.contextLinesBefore
                contextLinesAfter = node.contextLinesAfter
            }
        }
        
        fun toBookmarkNode(serialNode: SerializableNode): BookmarkNode {
            return BookmarkNode(
                id = serialNode.id,
                bookmarkId = serialNode.bookmarkId,
                displayName = serialNode.displayName,
                filePath = serialNode.filePath,
                lineNumber = serialNode.lineNumber,
                positionX = serialNode.positionX,
                positionY = serialNode.positionY,
                showCodeSnippet = serialNode.showCodeSnippet,
                contextLinesBefore = serialNode.contextLinesBefore,
                contextLinesAfter = serialNode.contextLinesAfter
            )
        }
    }
}

/**
 * Serializable version of NodeConnection
 */
class SerializableConnection {
    var id: String = ""
    var sourceNodeId: String = ""
    var targetNodeId: String = ""
    var label: String = ""
    var colorRGB: Int = 0
    
    companion object {
        fun fromNodeConnection(connection: NodeConnection): SerializableConnection {
            return SerializableConnection().apply {
                id = connection.id
                sourceNodeId = connection.sourceNodeId
                targetNodeId = connection.targetNodeId
                label = connection.label
                colorRGB = connection.colorRGB
            }
        }
        
        fun toNodeConnection(serialConn: SerializableConnection): NodeConnection {
            return NodeConnection(
                id = serialConn.id,
                sourceNodeId = serialConn.sourceNodeId,
                targetNodeId = serialConn.targetNodeId,
                label = serialConn.label,
                colorRGB = serialConn.colorRGB
            )
        }
    }
}

@State(
    name = "BookmarkCanvasPersistence",
    storages = [Storage("bookmarkCanvas.xml")]
)
class CanvasPersistenceService : PersistentStateComponent<MutableMap<String, PersistentCanvasState>> {
    private val LOG = Logger.getInstance(CanvasPersistenceService::class.java)
    
    // In-memory storage for canvas states
    private val projectCanvasMap = mutableMapOf<String, CanvasState>()
    // Storage for serialized state
    private var serializedState = mutableMapOf<String, PersistentCanvasState>()
    
    companion object {
        fun getInstance(): CanvasPersistenceService {
            return ApplicationManager.getApplication().getService(CanvasPersistenceService::class.java)
        }
    }

    fun getCanvasState(project: Project): CanvasState {
        val projectId = project.locationHash
        
        // If we already have it in memory, return it
        if (projectCanvasMap.containsKey(projectId)) {
            return projectCanvasMap[projectId]!!
        }
        
        // Try to get from serialized state
        val persistentState = serializedState[projectId]
        
        if (persistentState != null) {
            try {
                // Convert from serializable format to runtime format
                val canvasState = CanvasState()
                
                // Convert nodes
                for (serialNode in persistentState.nodeMap.values) {
                    val node = SerializableNode.toBookmarkNode(serialNode)
                    canvasState.addNode(node)
                }
                
                // Convert connections
                for (serialConn in persistentState.connections) {
                    val connection = SerializableConnection.toNodeConnection(serialConn)
                    canvasState.addConnection(connection)
                }
                
                projectCanvasMap[projectId] = canvasState
                return canvasState
            } catch (e: Exception) {
                LOG.error("Failed to deserialize canvas state: ${e.message}")
            }
        }
        
        // If all else fails, return a new empty state
        val newState = CanvasState()
        projectCanvasMap[projectId] = newState
        return newState
    }

    fun saveCanvasState(project: Project, canvasState: CanvasState) {
        val projectId = project.locationHash
        projectCanvasMap[projectId] = canvasState
        
        // Convert to serializable format
        val persistentState = PersistentCanvasState()
        persistentState.projectId = projectId
        
        // Convert nodes
        for (node in canvasState.nodes.values) {
            val serialNode = SerializableNode.fromBookmarkNode(node)
            persistentState.nodeMap[node.id] = serialNode
        }
        
        // Convert connections
        for (connection in canvasState.connections) {
            val serialConn = SerializableConnection.fromNodeConnection(connection)
            persistentState.connections.add(serialConn)
        }
        
        // Update serialized state
        serializedState[projectId] = persistentState
    }

    override fun getState(): MutableMap<String, PersistentCanvasState> {
        // Make sure any pending changes are serialized
        for ((projectId, canvasState) in projectCanvasMap) {
            val persistentState = PersistentCanvasState()
            persistentState.projectId = projectId
            
            for (node in canvasState.nodes.values) {
                val serialNode = SerializableNode.fromBookmarkNode(node)
                persistentState.nodeMap[node.id] = serialNode
            }
            
            for (connection in canvasState.connections) {
                val serialConn = SerializableConnection.fromNodeConnection(connection)
                persistentState.connections.add(serialConn)
            }
            
            serializedState[projectId] = persistentState
        }
        
        return serializedState
    }

    override fun loadState(state: MutableMap<String, PersistentCanvasState>) {
        serializedState = state
        // Clear in-memory map to force reload from serialized state on next access
        projectCanvasMap.clear()
    }
}