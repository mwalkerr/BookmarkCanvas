package org.mwalker.bookmarkcanvas.ui

import com.intellij.openapi.project.Project
import org.mwalker.bookmarkcanvas.model.BookmarkNode
import org.mwalker.bookmarkcanvas.model.NodeConnection
import org.mwalker.bookmarkcanvas.services.CanvasPersistenceService
import org.mwalker.bookmarkcanvas.ui.CanvasColors
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.Line2D
import java.awt.geom.Point2D

/**
 * Manages connections between nodes on the canvas
 */
class CanvasConnectionManager(
    private val canvasPanel: CanvasPanel,
    private val project: Project
) {
    /**
     * Creates a new connection between two bookmark nodes.
     * If a connection already exists between the nodes, it will be removed instead.
     */
    fun createNewConnection(source: BookmarkNode, target: BookmarkNode) {
        // Check if connection already exists
        val existingConnection = canvasPanel.canvasState.connections.find { 
            (it.sourceNodeId == source.id && it.targetNodeId == target.id) ||
            (it.sourceNodeId == target.id && it.targetNodeId == source.id)
        }
        
        if (existingConnection != null) {
            // Remove existing connection
            canvasPanel.canvasState.removeConnection(existingConnection.id)
        } else {
            // Create new connection
            val connection = NodeConnection(source.id, target.id)
            canvasPanel.canvasState.addConnection(connection)
        }
        
        CanvasPersistenceService.getInstance().saveCanvasState(project, canvasPanel.canvasState)
        canvasPanel.repaint()
    }
    
    /**
     * Draws a connection between two nodes
     */
    fun drawConnection(g2d: Graphics2D, source: NodeComponent, target: NodeComponent, color: Color) {
        // Calculate center points
        val startPoint = Point(
            source.x + source.width / 2,
            source.y + source.height / 2
        )

        val endPoint = Point(
            target.x + target.width / 2,
            target.y + target.height / 2
        )

        // Draw the line
        g2d.color = color
        g2d.stroke = BasicStroke(2.0f)
        g2d.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y)

        // Draw the arrowhead
        drawArrowHead(g2d, startPoint, endPoint)
    }
    
    /**
     * Draws a temporary connection during connection creation
     */
    fun drawTemporaryConnection(g2d: Graphics2D, startNode: NodeComponent, endPoint: Point) {
        val startPoint = Point(
            startNode.x + startNode.width / 2,
            startNode.y + startNode.height / 2
        )
        g2d.color = CanvasColors.CONNECTION_COLOR
        g2d.stroke = BasicStroke(
            2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            0f, floatArrayOf(5f), 0f
        )
        g2d.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y)
    }
    
    /**
     * Draws an arrow head at the end of a connection
     */
    private fun drawArrowHead(g2d: Graphics2D, start: Point, end: Point) {
        // Calculate arrowhead
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = Math.sqrt((dx * dx + dy * dy).toDouble())
        val dirX = dx / length
        val dirY = dy / length

        val arrowSize = 10.0
        val arrowAngle = Math.PI / 6 // 30 degrees

        val p1 = Point2D.Double(
            end.x - arrowSize * (dirX * Math.cos(arrowAngle) + dirY * Math.sin(arrowAngle)),
            end.y - arrowSize * (dirY * Math.cos(arrowAngle) - dirX * Math.sin(arrowAngle))
        )

        val p2 = Point2D.Double(
            end.x - arrowSize * (dirX * Math.cos(arrowAngle) - dirY * Math.sin(arrowAngle)),
            end.y - arrowSize * (dirY * Math.cos(arrowAngle) + dirX * Math.sin(arrowAngle))
        )

        // Draw arrowhead
        g2d.draw(Line2D.Double(end.x.toDouble(), end.y.toDouble(), p1.x, p1.y))
        g2d.draw(Line2D.Double(end.x.toDouble(), end.y.toDouble(), p2.x, p2.y))
    }
}