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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
//                LOG.info("Converted node to serializable: $it")
            }
        }
        
        fun toBookmarkNode(serialNode: SerializableNode): BookmarkNode {
//            LOG.info("Converting serializable node to BookmarkNode: $serialNode")
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
    
    // Coroutine scope for background operations
    @Transient
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // Keep track of save jobs to prevent concurrent modifications
    @Transient
    private val saveJobs = mutableMapOf<String, Job>()
    
    companion object {
        private val LOG = Logger.getInstance(CanvasPersistenceService::class.java)
        
        fun getInstance(): CanvasPersistenceService {
            return ApplicationManager.getApplication().getService(CanvasPersistenceService::class.java)
        }
    }
    
    init {
        LOG.info("CanvasPersistenceService initialized with state size: ${state.projectStates.size}")
    }

    fun getCanvasState(project: Project): CanvasState {
        val projectId = project.locationHash
        
        // If we already have it in memory, return it
        if (projectCanvasMap.containsKey(projectId)) {
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
        }
        
        // If all else fails, return a new empty state
        val newState = CanvasState()
        projectCanvasMap[projectId] = newState
        return newState
    }

    /**
     * Asynchronously saves the canvas state to persistent storage.
     * Uses Kotlin coroutines from IntelliJ's bundled libraries to avoid blocking the UI thread.
     */
    fun saveCanvasState(project: Project, canvasState: CanvasState) {
        // Store in memory immediately (this is fast)
        val projectId = project.locationHash
        projectCanvasMap[projectId] = canvasState
        
        // Cancel any previously running save job for this project
        saveJobs[projectId]?.cancel()
        
        // Start a new background job to save the state
        val job = coroutineScope.launch {
            try {
                // Create a snapshot of the current state to work with
                val stateCopy = CanvasState()
                stateCopy.snapToGrid = canvasState.snapToGrid
                stateCopy.showGrid = canvasState.showGrid
                stateCopy.zoomFactor = canvasState.zoomFactor
                stateCopy.scrollPositionX = canvasState.scrollPositionX
                stateCopy.scrollPositionY = canvasState.scrollPositionY
                
                // Copy nodes and connections
                for (node in canvasState.nodes.values) {
                    stateCopy.addNode(node.copy())
                }
                for (connection in canvasState.connections) {
                    stateCopy.addConnection(connection.copy())
                }
                
                // Convert to serializable format on background thread
                val persistentState = PersistentCanvasState()
                persistentState.projectId = projectId
                
                // Convert nodes
                for (node in stateCopy.nodes.values) {
                    val serialNode = SerializableNode.fromBookmarkNode(node)
                    persistentState.nodeMap[node.id] = serialNode
                }
                
                // Convert connections
                for (connection in stateCopy.connections) {
                    val serialConn = SerializableConnection.fromNodeConnection(connection)
                    persistentState.connections.add(serialConn)
                }
                
                // Save grid preferences and view state
                persistentState.snapToGrid = stateCopy.snapToGrid
                persistentState.showGrid = stateCopy.showGrid
                persistentState.zoomFactor = stateCopy.zoomFactor.toFloat()
                persistentState.scrollPositionX = stateCopy.scrollPositionX
                persistentState.scrollPositionY = stateCopy.scrollPositionY
                
                // Switch to UI thread to update the state safely
                withContext(Dispatchers.Main) {
                    if (!project.isDisposed) {
                        // Update serialized state
                        state.projectStates.put(projectId, persistentState)
                        LOG.info("Canvas state for project ${project.name} saved successfully")
                    }
                }
            } catch (e: Exception) {
                LOG.error("Error saving canvas state: ${e.message}", e)
            } finally {
                // Clean up job reference
                saveJobs.remove(projectId)
            }
        }
        
        // Store job for cancellation if needed
        saveJobs[projectId] = job
    }
}