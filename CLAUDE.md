# BookmarkCanvas Development Guide

## Build & Run Commands
- Build plugin: `./gradlew build`
- Run in IDE: `./gradlew runIde`
- Run tests: `./gradlew test`
- Run single test: `./gradlew test --tests "org.mwalker.bookmarkcanvas.TestClassName"`
- Clean: `./gradlew clean`
- Verify plugin: `./gradlew verifyPlugin`

## Code Style Guidelines
- **Language**: Kotlin 1.9.25 with JVM target 17
- **Naming**: Use camelCase for methods/variables, PascalCase for classes
- **Formatting**: 4-space indentation, max line length 120 characters
- **Imports**: Organize imports, no wildcards, alphabetical order
- **Classes**: Prefer data classes for models, single responsibility pattern
- **Nullability**: Use nullable types (Type?) explicitly, avoid !! operator
- **Error handling**: Use try/catch for expected exceptions, return Result for operations
- **UI Components**: Use IntelliJ's UI components (JBColor, JBPanel) for theme consistency
- **Architecture**: Follow MVC pattern with services layer for business logic

## Project Structure
- Actions: Canvas-related user actions
- Model: Data classes for bookmarks, canvas state
- Services: Business logic and persistence
- UI: Visual components and rendering