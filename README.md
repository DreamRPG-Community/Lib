# Lib

Shared Paper 1.12.2 runtime services for the self-written plugins.

## Provides

- Bukkit lifecycle and service registration
- Async and main-thread task helpers
- Command routing, usage, and completion support
- Inventory menu lifecycle and pagination
- Safe paths, managed path resolution, atomic YAML storage, rollback snapshots, and item serialization
- List policies and an embedded HTTP server
- Bounded WebRequest/WebResponse handling, token authentication, JSON/Form/Multipart parsing, and common HTTP errors
- Constructor-injected plugin bootstrap with `@InjectComponent`, `@ListenerComponent`,
  `@CommandComponent`, and `@ServiceComponent`
- Verified plugin-owned runtime library loading, standard JDBC/SQLite access, SQL migrations,
  template rendering, and scoreboard session lifecycle
- MythicThePit-compatible administrator location snapping for block centers and 45-degree view angles
- Reflection-isolated Paper 1.12.2 container animation packets with observer counting and
  automatic quit/teleport cleanup
- Shared player-loading gate with fixed blindness/jump restrictions and stateful menu callbacks

## Annotation bootstrap

Dependent plugins keep their Bukkit entry point small and expose one package-scoped component graph:

```java
LibApi lib = LibApi.require(this);
bootstrap = lib.createPluginBootstrap(this, "com.example.plugin");
bootstrap.enable();
```

Concrete components use one non-private constructor. Lib resolves constructor parameters, caches
singletons, registers annotated listeners, commands, and ServicesManager providers, and invokes
`LibPluginLifecycle` modules in a deterministic order. Runtime dependencies are never shaded into
the dependent plugin JAR.

## Runtime libraries

`LibraryService` verifies a fixed SHA-256 before loading a dependency from the requesting plugin's
`plugins/<PluginName>/libs/` directory. A verified cache starts offline. Paper 1.12.2 exposes only
the inherited protected `URLClassLoader.addURL`; JDK 21 JPMS blocks reflective access to that JDK
method, so Lib creates a child URL class loader with the Paper plugin loader as its parent. The
returned loader is the one that must be used for reflective driver loading. Dependency changes
require a full server restart.

`ScoreboardSession` synchronizes sidebar scores and teams by diffing the previous rendered state,
and can own a stable `BELOW_NAME` objective such as Bukkit's native `health` criterion. It does not
clear all scores or unregister every team during a normal refresh; callers should update
the session before showing a newly created viewer scoreboard. Team updates remain separate from
the full player-list display name so consumers can control TAB text directly.

## Container animations and loading state

`ContainerAnimationService` is the common boundary for client-side block animations. DreamRPG
passes a real ender-chest block and `ContainerAnimationSpec.enderChest()`; Lib resolves the
running CraftBukkit/NMS version, constructs `PacketPlayOutBlockAction` reflectively, broadcasts
the observer count within 64 blocks, and owns idempotent session cleanup on quit, kick, teleport,
or plugin shutdown. Unsupported NMS signatures fail the dependent plugin during startup instead
of silently opening an unanimated menu.

`PlayerLoadingGate` is also Lib-owned. A dependent plugin calls `begin`, loads data asynchronously,
then calls `ready` or `cancel`. While loading, movement and interaction are cancelled and the
fixed blindness/jump effects are refreshed every ten ticks; the gate restores effects that were
present before loading.

## Build

Requires JDK 21.

```powershell
.\gradlew.bat clean build check --warning-mode all
```

Dependent plugins should declare `depend: [Lib]` and resolve the service with
`LibApi.require(this)`.
