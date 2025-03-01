package org.mwalker.bookmarkcanvas.services

import org.mwalker.bookmarkcanvas.model.CanvasState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

@State(
    name = "BookmarkCanvasPersistence",
    storages = [
        Storage("bookmarkCanvas.xml")
    ]
)
class CanvasPersistenceService : PersistentStateComponent<CanvasPersistenceService> {
    private val projectCanvasMap = mutableMapOf<String, org.mwalker.bookmarkcanvas.model.CanvasState>()

    companion object {
        fun getInstance(): CanvasPersistenceService {
            return ApplicationManager.getApplication().getService(CanvasPersistenceService::class.java)
        }
    }

    fun getCanvasState(project: Project): org.mwalker.bookmarkcanvas.model.CanvasState {
        val projectId = project.locationHash
        return projectCanvasMap.getOrPut(projectId) { org.mwalker.bookmarkcanvas.model.CanvasState() }
    }

    fun saveCanvasState(project: Project, canvasState: org.mwalker.bookmarkcanvas.model.CanvasState) {
        val projectId = project.locationHash
        projectCanvasMap[projectId] = canvasState
    }

    @Nullable
    override fun getState(): CanvasPersistenceService? {
        return this
    }

    override fun loadState(@NotNull state: CanvasPersistenceService) {
        XmlSerializerUtil.copyBean(state, this)
    }
}