#!/bin/bash

# Script to restart the runIde process
# Usage: ./restart-runide.sh

PID_FILE="runide.pid"
LOG_FILE="runIde.log"

echo "🔄 Restarting runIde process..."

# Kill existing process if it exists
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "🛑 Killing existing process (PID: $OLD_PID)"
        kill "$OLD_PID"
        # Wait a moment for the process to terminate
        sleep 2
        # Force kill if still running
        if kill -0 "$OLD_PID" 2>/dev/null; then
            echo "🔥 Force killing process"
            kill -9 "$OLD_PID"
        fi
    else
        echo "🗑️  Old PID file found but process not running"
    fi
    rm -f "$PID_FILE"
fi

# Clean up any zombie Gradle processes
echo "🧹 Cleaning up zombie Gradle processes..."
GRADLE_PIDS=$(ps aux | grep -E "(gradle|kotlin)" | grep -v grep | awk '{print $2}')
if [ ! -z "$GRADLE_PIDS" ]; then
    echo "🛑 Found zombie processes: $GRADLE_PIDS"
    echo "$GRADLE_PIDS" | xargs kill -9 2>/dev/null || true
fi

# Stop any running Gradle daemons
echo "🛑 Stopping Gradle daemons..."
./gradlew --stop 2>/dev/null || true

# Start new process
echo "🚀 Starting new runIde process..."
nohup ./gradlew runIde > "$LOG_FILE" 2>&1 &
NEW_PID=$!

# Save the new PID
echo "$NEW_PID" > "$PID_FILE"

echo "✅ Started runIde with PID: $NEW_PID"
echo "📝 Logs: tail -f $LOG_FILE"
echo "🛑 Stop: kill $NEW_PID (or just run this script again)"

# Show initial log output
echo ""
echo "📋 Initial output:"
sleep 2
tail -10 "$LOG_FILE"