package org.mwalker.bookmarkcanvas.model

import java.awt.Color
import java.util.*

data class NodeConnection(
    val id: String = UUID.randomUUID().toString(),
    val sourceNodeId: String,
    val targetNodeId: String,
    var label: String = "",
    var colorRGB: Int = Color.GRAY.rgb
) {
    
    // Color as a computed property
    var color: Color
        get() = Color(colorRGB)
        set(value) {
            colorRGB = value.rgb
        }
    
    constructor(sourceNodeId: String, targetNodeId: String) : this(
        id = UUID.randomUUID().toString(),
        sourceNodeId = sourceNodeId,
        targetNodeId = targetNodeId
    )
}