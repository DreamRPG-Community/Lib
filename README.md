# Lib

Shared Paper 1.12.2 runtime services for the self-written plugins.

## Provides

- Bukkit lifecycle and service registration
- Async and main-thread task helpers
- Command routing, usage, and completion support
- Inventory menu lifecycle and pagination
- Safe paths, atomic file storage, and item serialization
- List policies and an embedded HTTP server

## Build

Requires JDK 21.

```powershell
.\gradlew.bat clean build check --warning-mode all
```

Dependent plugins should declare `depend: [Lib]` and resolve the service with
`LibApi.require(this)`.
