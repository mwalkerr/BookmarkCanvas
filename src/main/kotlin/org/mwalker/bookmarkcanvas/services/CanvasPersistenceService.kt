package org.mwalker.bookmarkcanvas.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.model.CanvasState
import org.mwalker.bookmarkcanvas.model.NodeConnection
import com.intellij.util.xmlb.annotations.Transient

/**
 * A state class specifically designed for serialization.
 * This is used as an intermediate format for the complex CanvasState.
 */
class PersistentCanvasState : BaseState() {
    var projectId by string("")
    var nodeMap by map<String, SerializableNode>()
    var connections by list<SerializableConnection>()
    var snapToGrid by property(false)
    var showGrid by property(false)
    var zoomFactor by property(1.0f) {
        if (it < 0.1) 0.1f
        else if (it > 2.0) 2.0f
        else it
    }
    var scrollPositionX by property(0)
    var scrollPositionY by property(0)
}

/**
 * Serializable version of BookmarkNode
 */
class SerializableNode : BaseState() {
    var id by string("")
    var bookmarkId by string("")
    var displayName by string(null)
    var filePath by string("")
    var lineContent by string("")
    var lineNumber by property(0)
    var positionX by property(100)
    var positionY by property(100)
    var width by property(0)
    var height by property(0)
    var showCodeSnippet by property(false)
    var contextLinesBefore by property(3)
    var contextLinesAfter by property(3)
    
    companion object {
        private val LOG = Logger.getInstance(SerializableNode::class.java)
        fun fromBookmarkNode(node: BookmarkNode): SerializableNode {
            return SerializableNode().apply {
                id = node.id
                bookmarkId = node.bookmarkId
                displayName = node.displayName
                filePath = node.filePath
                lineContent = node.lineContent ?: ""
                lineNumber = node.lineNumber0Based
                positionX = node.positionX
                positionY = node.positionY
                width = node.width
                height = node.height
                showCodeSnippet = node.showCodeSnippet
                contextLinesBefore = node.contextLinesBefore
                contextLinesAfter = node.contextLinesAfter
            }.also {
                LOG.info("Converted node to serializable: $it")
            }
        }
        
        fun toBookmarkNode(serialNode: SerializableNode): BookmarkNode {
            LOG.info("Converting serializable node to BookmarkNode: $serialNode")
            return BookmarkNode(
                id = serialNode.id ?: "",
                bookmarkId = serialNode.bookmarkId ?: "",
                displayName = serialNode.displayName,
                filePath = serialNode.filePath ?: "",
                lineContent = serialNode.lineContent,
                lineNumber0Based = serialNode.lineNumber,
                positionX = serialNode.positionX,
                positionY = serialNode.positionY,
                width = serialNode.width,
                height = serialNode.height,
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
class SerializableConnection : BaseState() {
    var id by string("")
    var sourceNodeId by string("")
    var targetNodeId by string("")
    var label by string("")
    var colorRGB by property(0)
    
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
                id = serialConn.id ?: "",
                sourceNodeId = serialConn.sourceNodeId ?: "",
                targetNodeId = serialConn.targetNodeId ?: "",
                label = serialConn.label ?: "",
                colorRGB = serialConn.colorRGB
            )
        }
    }
}

/**
 * Main state holder for the PersistentStateComponent
 */
class BookmarkCanvasState : BaseState() {
    var projectStates by map<String, PersistentCanvasState>()
}

@State(
    name = "BookmarkCanvasPersistence",
    storages = [Storage("bookmarkCanvas.xml")]
)
class CanvasPersistenceService : SimplePersistentStateComponent<BookmarkCanvasState>(BookmarkCanvasState()) {
    private val LOG = Logger.getInstance(CanvasPersistenceService::class.java)
    
    // In-memory storage for runtime canvas states
    @Transient
    private val projectCanvasMap = mutableMapOf<String, CanvasState>()
    
    companion object {
        private val LOG = Logger.getInstance(CanvasPersistenceService::class.java)
        
        fun getInstance(): CanvasPersistenceService {
            LOG.info("Getting CanvasPersistenceService instance")
            return ApplicationManager.getApplication().getService(CanvasPersistenceService::class.java)
        }
    }
    
    init {
        LOG.info("CanvasPersistenceService initialized with state size: ${state.projectStates.size}")
    }

    fun getCanvasState(project: Project): CanvasState {
        LOG.info("Getting canvas state for project: ${project.name}")

        val projectId = project.locationHash
        
        // If we already have it in memory, return it
        if (projectCanvasMap.containsKey(projectId)) {
            LOG.info("Returning existing in-memory state for project $projectId")
            return projectCanvasMap[projectId]!!
        }
        
        // Try to get from serialized state
        val persistentState = state.projectStates[projectId]
        
        if (persistentState != null) {
            try {
                LOG.info("Deserializing saved state for project $projectId with ${persistentState.nodeMap.size} nodes")
                
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
                
                // Set grid preferences and view state
                canvasState.snapToGrid = persistentState.snapToGrid
                canvasState.showGrid = persistentState.showGrid
                canvasState.zoomFactor = persistentState.zoomFactor.toDouble()
                canvasState.scrollPositionX = persistentState.scrollPositionX
                canvasState.scrollPositionY = persistentState.scrollPositionY
                
                projectCanvasMap[projectId] = canvasState
                return canvasState
            } catch (e: Exception) {
                LOG.error("Failed to deserialize canvas state: ${e.message}", e)
            }
        } else {
            LOG.info("No saved state found for project $projectId")
        }
        
        // If all else fails, return a new empty state
        val newState = CanvasState()
        projectCanvasMap[projectId] = newState
        return newState
    }

    fun saveCanvasState(project: Project, canvasState: CanvasState) {
        LOG.info("Saving canvas state for project: ${project.name} with ${canvasState.nodes.size} nodes")
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
        
        // Save grid preferences and view state
        persistentState.snapToGrid = canvasState.snapToGrid
        persistentState.showGrid = canvasState.showGrid
        persistentState.zoomFactor = canvasState.zoomFactor.toFloat()
        persistentState.scrollPositionX = canvasState.scrollPositionX
        persistentState.scrollPositionY = canvasState.scrollPositionY
        
        // Update serialized state
        state.projectStates.put(projectId, persistentState)
        
        // Make sure we commit changes to storage
        LOG.info("State updated, project count: ${state.projectStates.size}")
    }
}