# BookmarkCanvas

This plugin provides a canvas for organizing bookmarks in IntelliJ IDEs such as IntelliJ IDEA, PyCharm, Android Studio, and WebStorm.
The goal is to make it easy to create a visual representation both of bookmarks and of the relationships between them.
The canvas should make it easy to quickly jump to a bookmarked location

## Features

- Add a bookmark to the canvas from the "Bookmarks" tool window
- Add a bookmark to the canvas from the "Bookmarks" modal dialog
- Add a bookmark to the canvas from the canvas itself
- Navigate to a bookmark from the canvas by double clicking on it
- Remove a bookmark from the canvas
- Move a bookmark around the canvas
- Zoom in and out on the canvas
- Pan around the canvas
- Save the canvas layout to a file
- Load a canvas layout from a file
- Create connections between bookmarks
- Remove a connection between bookmarks
- Configure the amount of context to show around a bookmark (e.g. 5 lines above and below)
- Configure the layout of the canvas (e.g. grid (with snap to grid), freeform)
- Change the title for a node
- show/hide the code for a node (ie. the node can show just the title or the title and the code)

## Getting Started

### Prerequisites

- IntelliJ IDEA 2023.2.6+
- Java 17+

### Installation

1. Build the plugin using Gradle:
   ```
   ./gradlew build
   ```

2. Install the plugin in IntelliJ IDEA:
   - Go to Settings/Preferences > Plugins > ⚙️ > Install Plugin from Disk
   - Select the generated ZIP file from `build/distributions/`

## Usage

<!-- Add usage instructions -->

## Development

See [CLAUDE.md](CLAUDE.md) for development guidelines.

### TODO
- automatically adjusting line when file content changes
- syntax highlighting for code in the node

## License

<!-- Add license information -->