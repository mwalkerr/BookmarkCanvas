class WebCanvas {
    constructor() {
        this.canvas = document.getElementById('canvas');
        this.ctx = this.canvas.getContext('2d');
        this.nodesContainer = document.getElementById('nodes-container');
        this.selectionBox = document.getElementById('selection-box');
        this.tempConnection = document.getElementById('temp-connection');
        
        // Canvas state
        this.nodes = new Map();
        this.connections = [];
        this.selectedNodes = new Set();
        this.zoomFactor = 1.0;
        this.panX = 0;
        this.panY = 0;
        this.showGrid = false;
        this.snapToGrid = false;
        this.gridSize = 20;
        
        // Interaction state
        this.isDragging = false;
        this.isSelecting = false;
        this.isPanning = false;
        this.isConnecting = false;
        this.dragStartPoint = null;
        this.selectionStart = null;
        this.connectionStartNode = null;
        this.tempConnectionEnd = null;
        
        this.initializeEventListeners();
        this.setupBridge();
        this.resizeCanvas();
    }
    
    setupBridge() {
        // Bridge functions for communication with Kotlin backend
        window.canvasBridge = {
            addNode: (nodeData) => this.addNode(nodeData),
            removeNode: (nodeId) => this.removeNode(nodeId),
            addConnection: (connectionData) => this.addConnection(connectionData),
            removeConnection: (connectionId) => this.removeConnection(connectionId),
            clearCanvas: () => this.clearCanvas(),
            setGridSettings: (showGrid, snapToGrid) => this.setGridSettings(showGrid, snapToGrid),
            setZoom: (zoom) => this.setZoom(zoom),
            refreshFromState: (state) => this.refreshFromState(state),
            getCanvasState: () => this.getCanvasState(),
            zoomIn: () => this.zoomIn(),
            zoomOut: () => this.zoomOut(),
            goHome: () => this.goHome()
        };
        
        // Notify Kotlin that the canvas is ready
        if (window.kotlinBridge) {
            window.kotlinBridge.onCanvasReady();
        }
    }
    
    initializeEventListeners() {
        // Canvas events
        this.canvas.addEventListener('mousedown', (e) => this.handleCanvasMouseDown(e));
        this.canvas.addEventListener('mousemove', (e) => this.handleCanvasMouseMove(e));
        this.canvas.addEventListener('mouseup', (e) => this.handleCanvasMouseUp(e));
        this.canvas.addEventListener('contextmenu', (e) => this.handleCanvasContextMenu(e));
        this.canvas.addEventListener('wheel', (e) => this.handleWheel(e));
        
        // Window events
        window.addEventListener('resize', () => this.resizeCanvas());
        
        // Context menu events
        this.setupContextMenus();
        
        // Keyboard events
        document.addEventListener('keydown', (e) => this.handleKeyDown(e));
    }
    
    setupContextMenus() {
        const contextMenu = document.getElementById('context-menu');
        const nodeContextMenu = document.getElementById('node-context-menu');
        
        // Hide menus when clicking elsewhere
        document.addEventListener('click', () => {
            contextMenu.style.display = 'none';
            nodeContextMenu.style.display = 'none';
        });
        
        // Canvas context menu handlers
        contextMenu.addEventListener('click', (e) => {
            const action = e.target.dataset.action;
            if (action) {
                this.handleContextMenuAction(action, this.lastContextMenuPoint);
                contextMenu.style.display = 'none';
            }
        });
        
        // Node context menu handlers
        nodeContextMenu.addEventListener('click', (e) => {
            const action = e.target.dataset.action;
            if (action) {
                this.handleNodeContextMenuAction(action, this.lastContextMenuNode);
                nodeContextMenu.style.display = 'none';
            }
        });
    }
    
    handleCanvasMouseDown(e) {
        if (e.button === 0) { // Left click
            this.dragStartPoint = { x: e.clientX, y: e.clientY };
            
            if (e.target === this.canvas) {
                if (e.ctrlKey || e.metaKey) {
                    this.isPanning = true;
                    this.canvas.style.cursor = 'grab';
                } else {
                    this.isSelecting = true;
                    this.selectionStart = this.screenToCanvas(e.clientX, e.clientY);
                    this.clearSelection();
                }
            }
        }
    }
    
    handleCanvasMouseMove(e) {
        if (this.isPanning && this.dragStartPoint) {
            const dx = e.clientX - this.dragStartPoint.x;
            const dy = e.clientY - this.dragStartPoint.y;
            this.panX += dx;
            this.panY += dy;
            this.dragStartPoint = { x: e.clientX, y: e.clientY };
            this.updateTransform();
        } else if (this.isSelecting && this.selectionStart) {
            const currentPoint = this.screenToCanvas(e.clientX, e.clientY);
            this.updateSelectionBox(this.selectionStart, currentPoint);
        } else if (this.isConnecting && this.connectionStartNode) {
            this.tempConnectionEnd = this.screenToCanvas(e.clientX, e.clientY);
            this.drawTempConnection();
        }
    }
    
    handleCanvasMouseUp(e) {
        if (this.isPanning) {
            this.isPanning = false;
            this.canvas.style.cursor = 'default';
        } else if (this.isSelecting) {
            this.finalizeSelection();
            this.isSelecting = false;
            this.selectionBox.style.display = 'none';
        } else if (this.isConnecting) {
            const target = this.getNodeAt(e.clientX, e.clientY);
            if (target && target !== this.connectionStartNode) {
                this.createConnection(this.connectionStartNode, target);
            }
            this.stopConnecting();
        }
        
        this.dragStartPoint = null;
        this.selectionStart = null;
    }
    
    handleCanvasContextMenu(e) {
        e.preventDefault();
        this.lastContextMenuPoint = this.screenToCanvas(e.clientX, e.clientY);
        this.showContextMenu(e.clientX, e.clientY);
    }
    
    handleWheel(e) {
        e.preventDefault();
        const delta = e.deltaY > 0 ? 0.9 : 1.1;
        const mousePos = this.screenToCanvas(e.clientX, e.clientY);
        this.zoomAtPoint(delta, mousePos);
    }
    
    handleKeyDown(e) {
        if (e.key === 'Delete' || e.key === 'Backspace') {
            this.deleteSelectedNodes();
        } else if (e.key === 'Escape') {
            if (this.isConnecting) {
                this.stopConnecting();
            } else {
                this.clearSelection();
            }
        } else if (e.ctrlKey || e.metaKey) {
            if (e.key === 'a') {
                e.preventDefault();
                this.selectAllNodes();
            } else if (e.key === 'z') {
                e.preventDefault();
                if (e.shiftKey) {
                    this.redo();
                } else {
                    this.undo();
                }
            }
        }
    }
    
    addNode(nodeData) {
        const nodeElement = this.createNodeElement(nodeData);
        this.nodes.set(nodeData.id, {
            element: nodeElement,
            data: nodeData
        });
        this.nodesContainer.appendChild(nodeElement);
        this.updateNodePosition(nodeData.id);
    }
    
    createNodeElement(nodeData) {
        const node = document.createElement('div');
        node.className = 'node';
        node.dataset.nodeId = nodeData.id;
        
        if (nodeData.type === 'file') {
            node.classList.add('file-node');
        }
        
        const title = document.createElement('div');
        title.className = 'node-title';
        title.textContent = nodeData.title || 'Untitled';
        node.appendChild(title);
        
        if (nodeData.content) {
            const content = document.createElement('div');
            content.className = 'node-content';
            if (nodeData.type === 'file' && nodeData.content.startsWith('```')) {
                content.className += ' code-snippet';
            }
            content.textContent = nodeData.content;
            node.appendChild(content);
        }
        
        if (nodeData.url) {
            const url = document.createElement('div');
            url.className = 'node-url';
            url.textContent = nodeData.url;
            node.appendChild(url);
        }
        
        const resizeHandle = document.createElement('div');
        resizeHandle.className = 'node-resize-handle';
        node.appendChild(resizeHandle);
        
        this.setupNodeInteractions(node);
        
        return node;
    }
    
    setupNodeInteractions(node) {
        let isDragging = false;
        let dragStart = null;
        let initialPos = null;
        
        node.addEventListener('mousedown', (e) => {
            if (e.button === 0 && !e.target.classList.contains('node-resize-handle')) {
                e.stopPropagation();
                isDragging = true;
                dragStart = { x: e.clientX, y: e.clientY };
                initialPos = { 
                    x: parseInt(node.style.left) || 0, 
                    y: parseInt(node.style.top) || 0 
                };
                
                if (!this.selectedNodes.has(node)) {
                    if (!e.shiftKey) {
                        this.clearSelection();
                    }
                    this.selectNode(node);
                }
                
                node.classList.add('dragging');
            }
        });
        
        node.addEventListener('mousemove', (e) => {
            if (isDragging && dragStart) {
                const dx = e.clientX - dragStart.x;
                const dy = e.clientY - dragStart.y;
                
                this.selectedNodes.forEach(selectedNode => {
                    const nodeData = this.nodes.get(selectedNode.dataset.nodeId);
                    if (nodeData) {
                        const currentPos = {
                            x: (selectedNode === node ? initialPos.x : parseInt(selectedNode.style.left) || 0) + dx / this.zoomFactor,
                            y: (selectedNode === node ? initialPos.y : parseInt(selectedNode.style.top) || 0) + dy / this.zoomFactor
                        };
                        
                        if (this.snapToGrid) {
                            currentPos.x = Math.round(currentPos.x / this.gridSize) * this.gridSize;
                            currentPos.y = Math.round(currentPos.y / this.gridSize) * this.gridSize;
                        }
                        
                        selectedNode.style.left = currentPos.x + 'px';
                        selectedNode.style.top = currentPos.y + 'px';
                        
                        nodeData.data.positionX = currentPos.x;
                        nodeData.data.positionY = currentPos.y;
                    }
                });
                
                this.redrawConnections();
            }
        });
        
        node.addEventListener('mouseup', () => {
            if (isDragging) {
                isDragging = false;
                node.classList.remove('dragging');
                this.notifyNodePositionChanged();
            }
        });
        
        node.addEventListener('contextmenu', (e) => {
            e.preventDefault();
            e.stopPropagation();
            this.lastContextMenuNode = node;
            this.showNodeContextMenu(e.clientX, e.clientY);
        });
        
        node.addEventListener('dblclick', (e) => {
            e.stopPropagation();
            this.editNode(node);
        });
    }
    
    selectNode(node) {
        this.selectedNodes.add(node);
        node.classList.add('selected');
    }
    
    clearSelection() {
        this.selectedNodes.forEach(node => {
            node.classList.remove('selected');
        });
        this.selectedNodes.clear();
    }
    
    updateSelectionBox(start, end) {
        const left = Math.min(start.x, end.x);
        const top = Math.min(start.y, end.y);
        const width = Math.abs(end.x - start.x);
        const height = Math.abs(end.y - start.y);
        
        this.selectionBox.style.left = left + 'px';
        this.selectionBox.style.top = top + 'px';
        this.selectionBox.style.width = width + 'px';
        this.selectionBox.style.height = height + 'px';
        this.selectionBox.style.display = 'block';
        
        // Select nodes within the selection box
        this.clearSelection();
        this.nodes.forEach((nodeObj, nodeId) => {
            const nodeRect = nodeObj.element.getBoundingClientRect();
            const canvasRect = this.canvas.getBoundingClientRect();
            
            const nodeCanvasX = (nodeRect.left - canvasRect.left - this.panX) / this.zoomFactor;
            const nodeCanvasY = (nodeRect.top - canvasRect.top - this.panY) / this.zoomFactor;
            
            if (nodeCanvasX >= left && nodeCanvasX <= left + width &&
                nodeCanvasY >= top && nodeCanvasY <= top + height) {
                this.selectNode(nodeObj.element);
            }
        });
    }
    
    finalizeSelection() {
        // Selection is already updated in updateSelectionBox
    }
    
    addConnection(connectionData) {
        this.connections.push(connectionData);
        this.redrawConnections();
    }
    
    removeConnection(connectionId) {
        this.connections = this.connections.filter(conn => conn.id !== connectionId);
        this.redrawConnections();
    }
    
    createConnection(sourceNode, targetNode) {
        const sourceId = sourceNode.dataset.nodeId;
        const targetId = targetNode.dataset.nodeId;
        
        // Check if connection already exists
        const exists = this.connections.some(conn => 
            (conn.sourceNodeId === sourceId && conn.targetNodeId === targetId) ||
            (conn.sourceNodeId === targetId && conn.targetNodeId === sourceId)
        );
        
        if (!exists && window.kotlinBridge) {
            window.kotlinBridge.createConnection(sourceId, targetId);
        }
    }
    
    redrawConnections() {
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        
        if (this.showGrid) {
            this.drawGrid();
        }
        
        this.connections.forEach(connection => {
            this.drawConnection(connection);
        });
    }
    
    drawConnection(connection) {
        const sourceNode = this.nodes.get(connection.sourceNodeId);
        const targetNode = this.nodes.get(connection.targetNodeId);
        
        if (!sourceNode || !targetNode) return;
        
        const sourceRect = sourceNode.element.getBoundingClientRect();
        const targetRect = targetNode.element.getBoundingClientRect();
        const canvasRect = this.canvas.getBoundingClientRect();
        
        const sourceX = sourceRect.left + sourceRect.width / 2 - canvasRect.left;
        const sourceY = sourceRect.top + sourceRect.height / 2 - canvasRect.top;
        const targetX = targetRect.left + targetRect.width / 2 - canvasRect.left;
        const targetY = targetRect.top + targetRect.height / 2 - canvasRect.top;
        
        this.ctx.beginPath();
        this.ctx.moveTo(sourceX, sourceY);
        this.ctx.lineTo(targetX, targetY);
        this.ctx.strokeStyle = connection.color || '#6A9DDD';
        this.ctx.lineWidth = 2;
        this.ctx.stroke();
    }
    
    drawTempConnection() {
        if (!this.connectionStartNode || !this.tempConnectionEnd) return;
        
        const sourceRect = this.connectionStartNode.getBoundingClientRect();
        const canvasRect = this.canvas.getBoundingClientRect();
        
        const sourceX = sourceRect.left + sourceRect.width / 2 - canvasRect.left;
        const sourceY = sourceRect.top + sourceRect.height / 2 - canvasRect.top;
        const targetPos = this.canvasToScreen(this.tempConnectionEnd.x, this.tempConnectionEnd.y);
        
        this.ctx.save();
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        this.redrawConnections();
        
        this.ctx.beginPath();
        this.ctx.moveTo(sourceX, sourceY);
        this.ctx.lineTo(targetPos.x - canvasRect.left, targetPos.y - canvasRect.top);
        this.ctx.strokeStyle = '#FFA500';
        this.ctx.lineWidth = 2;
        this.ctx.setLineDash([5, 5]);
        this.ctx.stroke();
        this.ctx.restore();
    }
    
    drawGrid() {
        const scaledGridSize = this.gridSize * this.zoomFactor;
        
        this.ctx.strokeStyle = '#3E3E3E';
        this.ctx.lineWidth = 1;
        
        // Draw vertical lines
        for (let x = (this.panX % scaledGridSize); x < this.canvas.width; x += scaledGridSize) {
            this.ctx.beginPath();
            this.ctx.moveTo(x, 0);
            this.ctx.lineTo(x, this.canvas.height);
            this.ctx.stroke();
        }
        
        // Draw horizontal lines
        for (let y = (this.panY % scaledGridSize); y < this.canvas.height; y += scaledGridSize) {
            this.ctx.beginPath();
            this.ctx.moveTo(0, y);
            this.ctx.lineTo(this.canvas.width, y);
            this.ctx.stroke();
        }
    }
    
    setGridSettings(showGrid, snapToGrid) {
        this.showGrid = showGrid;
        this.snapToGrid = snapToGrid;
        
        if (showGrid) {
            document.getElementById('canvas-container').classList.add('grid-background');
        } else {
            document.getElementById('canvas-container').classList.remove('grid-background');
        }
        
        this.redrawConnections();
    }
    
    setZoom(zoom) {
        this.zoomFactor = zoom;
        this.updateTransform();
        this.redrawConnections();
    }
    
    zoomIn() {
        this.zoomFactor = Math.min(this.zoomFactor * 1.2, 3.0);
        this.updateTransform();
        this.redrawConnections();
    }
    
    zoomOut() {
        this.zoomFactor = Math.max(this.zoomFactor * 0.8, 0.3);
        this.updateTransform();
        this.redrawConnections();
    }
    
    zoomAtPoint(factor, point) {
        const oldZoom = this.zoomFactor;
        this.zoomFactor = Math.max(0.3, Math.min(3.0, this.zoomFactor * factor));
        
        const zoomChange = this.zoomFactor / oldZoom;
        this.panX = point.x - (point.x - this.panX) * zoomChange;
        this.panY = point.y - (point.y - this.panY) * zoomChange;
        
        this.updateTransform();
        this.redrawConnections();
        
        if (window.kotlinBridge) {
            window.kotlinBridge.onZoomChanged(this.zoomFactor);
        }
    }
    
    updateTransform() {
        this.nodesContainer.style.transform = `translate(${this.panX}px, ${this.panY}px) scale(${this.zoomFactor})`;
        this.nodesContainer.style.transformOrigin = '0 0';
    }
    
    updateNodePosition(nodeId) {
        const nodeObj = this.nodes.get(nodeId);
        if (nodeObj) {
            nodeObj.element.style.left = nodeObj.data.positionX + 'px';
            nodeObj.element.style.top = nodeObj.data.positionY + 'px';
        }
    }
    
    screenToCanvas(screenX, screenY) {
        const canvasRect = this.canvas.getBoundingClientRect();
        return {
            x: (screenX - canvasRect.left - this.panX) / this.zoomFactor,
            y: (screenY - canvasRect.top - this.panY) / this.zoomFactor
        };
    }
    
    canvasToScreen(canvasX, canvasY) {
        const canvasRect = this.canvas.getBoundingClientRect();
        return {
            x: canvasX * this.zoomFactor + this.panX + canvasRect.left,
            y: canvasY * this.zoomFactor + this.panY + canvasRect.top
        };
    }
    
    getNodeAt(screenX, screenY) {
        const elements = document.elementsFromPoint(screenX, screenY);
        for (const element of elements) {
            if (element.classList.contains('node')) {
                return element;
            }
        }
        return null;
    }
    
    resizeCanvas() {
        this.canvas.width = window.innerWidth;
        this.canvas.height = window.innerHeight;
        this.redrawConnections();
    }
    
    // Context menu actions
    showContextMenu(x, y) {
        const contextMenu = document.getElementById('context-menu');
        contextMenu.style.left = x + 'px';
        contextMenu.style.top = y + 'px';
        contextMenu.style.display = 'block';
    }
    
    showNodeContextMenu(x, y) {
        const nodeContextMenu = document.getElementById('node-context-menu');
        nodeContextMenu.style.left = x + 'px';
        nodeContextMenu.style.top = y + 'px';
        nodeContextMenu.style.display = 'block';
    }
    
    handleContextMenuAction(action, point) {
        if (window.kotlinBridge) {
            switch (action) {
                case 'add-bookmark':
                    window.kotlinBridge.addBookmark(point.x, point.y);
                    break;
                case 'clear-canvas':
                    window.kotlinBridge.clearCanvas();
                    break;
                case 'refresh-bookmarks':
                    window.kotlinBridge.refreshBookmarks();
                    break;
                case 'paste':
                    window.kotlinBridge.paste(point.x, point.y);
                    break;
            }
        }
    }
    
    handleNodeContextMenuAction(action, node) {
        const nodeId = node.dataset.nodeId;
        if (window.kotlinBridge) {
            switch (action) {
                case 'edit':
                    window.kotlinBridge.editNode(nodeId);
                    break;
                case 'copy':
                    window.kotlinBridge.copyNode(nodeId);
                    break;
                case 'delete':
                    window.kotlinBridge.deleteNode(nodeId);
                    break;
                case 'connect':
                    this.startConnecting(node);
                    break;
                case 'disconnect':
                    window.kotlinBridge.disconnectNode(nodeId);
                    break;
            }
        }
    }
    
    startConnecting(node) {
        this.isConnecting = true;
        this.connectionStartNode = node;
        node.classList.add('connecting');
        this.canvas.style.cursor = 'crosshair';
    }
    
    stopConnecting() {
        this.isConnecting = false;
        if (this.connectionStartNode) {
            this.connectionStartNode.classList.remove('connecting');
            this.connectionStartNode = null;
        }
        this.tempConnectionEnd = null;
        this.canvas.style.cursor = 'default';
        this.redrawConnections();
    }
    
    // State management
    refreshFromState(state) {
        this.clearCanvas();
        
        // Add nodes
        state.nodes.forEach(node => this.addNode(node));
        
        // Add connections
        state.connections.forEach(connection => this.addConnection(connection));
        
        // Update settings
        this.setGridSettings(state.showGrid, state.snapToGrid);
        this.setZoom(state.zoomFactor);
        this.panX = state.scrollPositionX || 0;
        this.panY = state.scrollPositionY || 0;
        this.updateTransform();
    }
    
    getCanvasState() {
        return {
            nodes: Array.from(this.nodes.values()).map(node => node.data),
            connections: this.connections,
            zoomFactor: this.zoomFactor,
            scrollPositionX: this.panX,
            scrollPositionY: this.panY,
            showGrid: this.showGrid,
            snapToGrid: this.snapToGrid
        };
    }
    
    clearCanvas() {
        this.nodes.clear();
        this.connections = [];
        this.selectedNodes.clear();
        this.nodesContainer.innerHTML = '';
        this.redrawConnections();
    }
    
    removeNode(nodeId) {
        const nodeObj = this.nodes.get(nodeId);
        if (nodeObj) {
            nodeObj.element.remove();
            this.nodes.delete(nodeId);
            this.selectedNodes.delete(nodeObj.element);
            
            // Remove connections involving this node
            this.connections = this.connections.filter(conn => 
                conn.sourceNodeId !== nodeId && conn.targetNodeId !== nodeId
            );
            
            this.redrawConnections();
        }
    }
    
    deleteSelectedNodes() {
        if (window.kotlinBridge) {
            this.selectedNodes.forEach(node => {
                window.kotlinBridge.deleteNode(node.dataset.nodeId);
            });
        }
    }
    
    selectAllNodes() {
        this.clearSelection();
        this.nodes.forEach((nodeObj) => {
            this.selectNode(nodeObj.element);
        });
    }
    
    editNode(node) {
        if (window.kotlinBridge) {
            window.kotlinBridge.editNode(node.dataset.nodeId);
        }
    }
    
    goHome() {
        this.panX = 0;
        this.panY = 0;
        this.zoomFactor = 1.0;
        this.updateTransform();
        this.redrawConnections();
    }
    
    undo() {
        if (window.kotlinBridge) {
            window.kotlinBridge.undo();
        }
    }
    
    redo() {
        if (window.kotlinBridge) {
            window.kotlinBridge.redo();
        }
    }
    
    notifyNodePositionChanged() {
        if (window.kotlinBridge) {
            const positions = {};
            this.selectedNodes.forEach(node => {
                const nodeData = this.nodes.get(node.dataset.nodeId);
                if (nodeData) {
                    positions[node.dataset.nodeId] = {
                        x: nodeData.data.positionX,
                        y: nodeData.data.positionY
                    };
                }
            });
            window.kotlinBridge.onNodePositionsChanged(positions);
        }
    }
}

// Initialize the canvas when the page loads
document.addEventListener('DOMContentLoaded', () => {
    window.webCanvas = new WebCanvas();
});