# MoxueBridge v0.1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a read-only Paper 1.21.1 plugin that exposes authenticated runtime plugin and player-facing capability information over a LAN HTTP API, beginning with deep VeinMiner discovery.

**Architecture:** MoxueBridge runs inside Paper on the server laptop. Paper/Bukkit runtime state is authoritative for loaded/enabled plugins; plugin-specific integrations translate trusted configuration into normalized capabilities stored in an immutable atomic snapshot. A small HTTP server reads only the latest snapshot and never touches Bukkit APIs from its worker threads.

**Tech Stack:** Java 21, Gradle Kotlin DSL, Paper API `1.21.1-R0.1-SNAPSHOT`, JUnit 5, Gson 2.11.0, JDK `HttpServer`, Windows PowerShell for build/deploy smoke tests.

**Spec:** `docs/superpowers/specs/2026-09-17-moxuebridge-design.md`

## Global Constraints

- Target server: Paper 1.21.1 on Java 21.
- Version 0.1.0 is strictly read-only: no console execution, OP, permissions changes, kick/ban, item give, teleport, world mutation, or plugin config writes other than MoxueBridge's own generated token/config.
- API default bind is `0.0.0.0:8766` because DC_BOT runs on a different LAN computer.
- Every `/api/v1/*` endpoint requires an `Authorization: Bearer ...` header.
- The bearer token is generated at first run with `SecureRandom`, saved only in MoxueBridge runtime config, and never committed or printed in full.
- Playit must never expose TCP 8766.
- Paper runtime state is authoritative; a JAR on disk alone never proves a plugin or capability is available.
- HTTP worker threads may only read immutable snapshots; Bukkit/Paper API discovery runs on the Paper main thread.
- Unknown plugins may expose trustworthy metadata/declared commands but must not produce invented deep capabilities.
- VeinMiner current baseline: `mustSneak=true`, `maxChain=100`, `needCorrectTool=true`, one `Ores` group using `#c:ores` and `#minecraft:pickaxes`; therefore `vein_mining` is present and `tree_felling` is absent.
- API JSON uses `lower_snake_case` field names to match the v1 design contract, including `bridge_version`, `server_software`, and `generated_at`.

---

## File Structure

```text
MoxueBridge/
?? .github/
?? ?? workflows/
??    ?? ci.yml
?? .gitignore
?? README.md
?? build.gradle.kts
?? settings.gradle.kts
?? gradle.properties
?? gradlew
?? gradlew.bat
?? gradle/wrapper/...
?? src/
?? ?? main/
?? ?? ?? java/io/github/neko0115/moxuebridge/
?? ?? ?? ?? MoxueBridgePlugin.java
?? ?? ?? ?? api/
?? ?? ?? ?? ?? BridgeHttpServer.java
?? ?? ?? ?? config/
?? ?? ?? ?? ?? BridgeConfiguration.java
?? ?? ?? ?? ?? TokenGenerator.java
?? ?? ?? ?? discovery/
?? ?? ?? ?? ?? BukkitPluginCatalog.java
?? ?? ?? ?? ?? PluginCatalog.java
?? ?? ?? ?? ?? RegistryBuilder.java
?? ?? ?? ?? integration/
?? ?? ?? ?? ?? IntegrationException.java
?? ?? ?? ?? ?? PluginIntegration.java
?? ?? ?? ?? ?? VeinMinerIntegration.java
?? ?? ?? ?? lifecycle/
?? ?? ?? ?? ?? PluginLifecycleListener.java
?? ?? ?? ?? model/
?? ?? ?? ?? ?? BridgeSnapshot.java
?? ?? ?? ?? ?? BridgeStatus.java
?? ?? ?? ?? ?? Capability.java
?? ?? ?? ?? ?? CapabilitySource.java
?? ?? ?? ?? ?? CapabilityUsage.java
?? ?? ?? ?? ?? CommandInfo.java
?? ?? ?? ?? ?? PluginInfo.java
?? ?? ?? ?? ?? RuntimePluginDescriptor.java
?? ?? ?? ?? registry/
?? ?? ?? ?? ?? CapabilityRegistry.java
?? ?? ?? ?? security/
?? ?? ??    ?? BearerTokenValidator.java
?? ?? ?? resources/
?? ??    ?? config.yml
?? ??    ?? plugin.yml
?? ?? test/
??    ?? java/io/github/neko0115/moxuebridge/...
??    ?? resources/veinminer/
??       ?? ores-only/settings.json
??       ?? ores-only/groups.json
??       ?? ores-and-logs/groups.json
?? docs/superpowers/...
```

---

### Task 1: Bootstrap the Java 21 / Paper 1.21.1 project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `src/main/resources/plugin.yml`
- Create: `src/main/resources/config.yml`
- Create: `src/main/java/io/github/neko0115/moxuebridge/MoxueBridgePlugin.java`
- Create: `src/test/java/io/github/neko0115/moxuebridge/PluginDescriptorResourceTest.java`
- Create: Gradle wrapper files using Gradle 8.10.2

**Interfaces:**
- Produces: Paper entrypoint `io.github.neko0115.moxuebridge.MoxueBridgePlugin`
- Produces: build artifact `build/libs/MoxueBridge-0.1.0.jar`
- Consumes: Paper API `1.21.1-R0.1-SNAPSHOT`

- [ ] **Step 1: Create the build files and wrapper**

`settings.gradle.kts`:

```kotlin
rootProject.name = "MoxueBridge"
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx1G -Dfile.encoding=UTF-8
org.gradle.parallel=true
```

`build.gradle.kts`:

```kotlin
plugins {
    java
}

group = "io.github.neko0115"
version = "0.1.0"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.google.code.gson:gson:2.11.0")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.code.gson:gson:2.11.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
```

Generate the wrapper from a machine with Gradle available:

```powershell
gradle wrapper --gradle-version 8.10.2
```

- [ ] **Step 2: Write the failing plugin descriptor resource test**

`src/test/java/io/github/neko0115/moxuebridge/PluginDescriptorResourceTest.java`:

```java
package io.github.neko0115.moxuebridge;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PluginDescriptorResourceTest {
    @Test
    void pluginYmlDeclaresExpectedEntrypointAndApiVersion() throws IOException {
        var stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream);
        var text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(text.contains("main: io.github.neko0115.moxuebridge.MoxueBridgePlugin"));
        assertTrue(text.contains("api-version: '1.21'"));
    }
}
```

- [ ] **Step 3: Run the test and verify RED**

```powershell
.\gradlew.bat test --tests "*PluginDescriptorResourceTest"
```

Expected: FAIL because `plugin.yml` does not exist yet.

- [ ] **Step 4: Add minimal Paper resources and plugin entrypoint**

`src/main/resources/plugin.yml`:

```yaml
name: MoxueBridge
version: '${version}'
main: io.github.neko0115.moxuebridge.MoxueBridgePlugin
api-version: '1.21'
author: neko0115
description: Read-only runtime capability bridge for Moxue clients.
libraries:
  - com.google.code.gson:gson:2.11.0
```

`src/main/resources/config.yml`:

```yaml
http:
  bind: "0.0.0.0"
  port: 8766
security:
  token: ""
```

`src/main/java/io/github/neko0115/moxuebridge/MoxueBridgePlugin.java`:

```java
package io.github.neko0115.moxuebridge;

import org.bukkit.plugin.java.JavaPlugin;

public final class MoxueBridgePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("MoxueBridge 0.1.0 enabled");
    }
}
```

- [ ] **Step 5: Run tests and build GREEN**

```powershell
.\gradlew.bat clean test build
```

Expected: PASS and `build\libs\MoxueBridge-0.1.0.jar` exists.

- [ ] **Step 6: Commit**

```powershell
git add settings.gradle.kts build.gradle.kts gradle.properties gradlew gradlew.bat gradle src/main src/test
git commit -m "build: bootstrap Paper 1.21.1 plugin"
```

---

### Task 2: Add immutable domain models and atomic registry

**Files:**
- Create: `src/main/java/io/github/neko0115/moxuebridge/model/BridgeStatus.java`
- Create: `src/main/java/io/github/neko0115/moxuebridge/model/CommandInfo.java`
- Create: `src/main/java/io/github/neko0115/moxuebridge/model/PluginInfo.java`
- Create: `src/main/java/io/github/neko0115/moxuebridge/model/CapabilitySource.java`
- Create: `src/main/java/io/github/neko0115/moxuebridge/model/CapabilityUsage.java`
- Create: `src/main/java/io/github/neko0115/moxuebridge/model/Capability.java`
- Create: `src/main/java/io/github/neko0115/moxuebridge/model/RuntimePluginDescriptor.java`
- Create: `src/main/java/io/github/neko0115/moxuebridge/model/BridgeSnapshot.java`
- Create: `src/main/java/io/github/neko0115/moxuebridge/registry/CapabilityRegistry.java`
- Create: `src/test/java/io/github/neko0115/moxuebridge/registry/CapabilityRegistryTest.java`

**Interfaces:**
- Produces: `CapabilityRegistry#snapshot(): BridgeSnapshot`
- Produces: `CapabilityRegistry#replace(BridgeSnapshot): void`
- Produces immutable records consumed by discovery, integrations, and HTTP serialization.

- [ ] **Step 1: Write the failing registry immutability test**

```java
package io.github.neko0115.moxuebridge.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.neko0115.moxuebridge.model.BridgeSnapshot;
import io.github.neko0115.moxuebridge.model.BridgeStatus;
import io.github.neko0115.moxuebridge.model.PluginInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilityRegistryTest {
    @Test
    void snapshotCopiesInputListsAndSwapsAtomically() {
        var plugins = new ArrayList<PluginInfo>();
        plugins.add(new PluginInfo("VeinMiner", "2.11.2", true, true, List.of()));
        var snapshot = new BridgeSnapshot(
                "2026-09-17T08:00:00Z",
                new BridgeStatus("MoxueBridge", "0.1.0", "1.21.1", "Paper", true),
                plugins,
                List.of());

        var registry = new CapabilityRegistry(snapshot);
        plugins.clear();

        assertEquals(1, registry.snapshot().plugins().size());
        assertThrows(UnsupportedOperationException.class,
                () -> registry.snapshot().plugins().add(null));
    }
}
```

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat test --tests "*CapabilityRegistryTest"
```

Expected: compile failure because model/registry classes do not exist.

- [ ] **Step 3: Implement the immutable records and registry**

Use records with defensive `List.copyOf(...)` and `Map.copyOf(...)` in compact constructors. `BridgeSnapshot` stores an ISO-8601 `generatedAt` string instead of `Instant` so Gson can serialize it without reflective Java-time adapters.

Required signatures:

```java
public record BridgeStatus(
        String bridge,
        String bridgeVersion,
        String minecraft,
        String serverSoftware,
        boolean online) {}
```

```java
public record CommandInfo(
        String name,
        String description,
        String usage,
        String permission,
        List<String> aliases) {
    public CommandInfo {
        aliases = List.copyOf(aliases);
    }
}
```

```java
public record PluginInfo(
        String name,
        String version,
        boolean enabled,
        boolean integrated,
        List<CommandInfo> commands) {
    public PluginInfo {
        commands = List.copyOf(commands);
    }
}
```

```java
public record CapabilitySource(String plugin, String version, String provenance) {}
public record CapabilityUsage(String trigger, String human) {}
```

```java
public record Capability(
        String id,
        String name,
        String description,
        boolean available,
        CapabilitySource source,
        CapabilityUsage usage,
        java.util.Map<String, Object> constraints) {
    public Capability {
        constraints = java.util.Map.copyOf(constraints);
    }
}
```

```java
public record RuntimePluginDescriptor(
        String name,
        String version,
        boolean enabled,
        java.nio.file.Path dataFolder,
        List<CommandInfo> commands) {
    public RuntimePluginDescriptor {
        commands = List.copyOf(commands);
    }
}
```

```java
public record BridgeSnapshot(
        String generatedAt,
        BridgeStatus status,
        List<PluginInfo> plugins,
        List<Capability> capabilities) {
    public BridgeSnapshot {
        plugins = List.copyOf(plugins);
        capabilities = List.copyOf(capabilities);
    }
}
```

`CapabilityRegistry`:

```java
public final class CapabilityRegistry {
    private final java.util.concurrent.atomic.AtomicReference<BridgeSnapshot> snapshot;

    public CapabilityRegistry(BridgeSnapshot initial) {
        this.snapshot = new java.util.concurrent.atomic.AtomicReference<>(initial);
    }

    public BridgeSnapshot snapshot() {
        return snapshot.get();
    }

    public void replace(BridgeSnapshot next) {
        snapshot.set(java.util.Objects.requireNonNull(next));
    }
}
```

- [ ] **Step 4: Run GREEN**

```powershell
.\gradlew.bat test --tests "*CapabilityRegistryTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/io/github/neko0115/moxuebridge/model src/main/java/io/github/neko0115/moxuebridge/registry src/test/java/io/github/neko0115/moxuebridge/registry
git commit -m "feat: add capability snapshot registry"
```

---

### Task 3: Add secure first-run bearer token primitives

**Files:**
- Create: `src/main/java/io/github/neko0115/moxuebridge/config/TokenGenerator.java`
- Create: `src/main/java/io/github/neko0115/moxuebridge/security/BearerTokenValidator.java`
- Create: `src/test/java/io/github/neko0115/moxuebridge/security/BearerTokenValidatorTest.java`

**Interfaces:**
- Produces: `TokenGenerator#generate(): String`
- Produces: `BearerTokenValidator#isAuthorized(String authorizationHeader): boolean`

- [ ] **Step 1: Write failing security tests**

```java
package io.github.neko0115.moxuebridge.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.neko0115.moxuebridge.config.TokenGenerator;
import org.junit.jupiter.api.Test;

class BearerTokenValidatorTest {
    @Test
    void acceptsOnlyExactBearerToken() {
        var validator = new BearerTokenValidator("abc123");
        assertTrue(validator.isAuthorized("Bearer abc123"));
        assertFalse(validator.isAuthorized(null));
        assertFalse(validator.isAuthorized("abc123"));
        assertFalse(validator.isAuthorized("Bearer wrong"));
    }

    @Test
    void generatedTokenIsLongAndUrlSafe() {
        var token = new TokenGenerator().generate();
        assertTrue(token.length() >= 43);
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
    }
}
```

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat test --tests "*BearerTokenValidatorTest"
```

Expected: compile failure because classes do not exist.

- [ ] **Step 3: Implement token generation and constant-time comparison**

`TokenGenerator` must generate 32 random bytes with `SecureRandom` and encode using `Base64.getUrlEncoder().withoutPadding()`.

`BearerTokenValidator` must reject null/non-Bearer headers and compare UTF-8 byte arrays using `MessageDigest.isEqual(...)`.

- [ ] **Step 4: Run GREEN**

```powershell
.\gradlew.bat test --tests "*BearerTokenValidatorTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/io/github/neko0115/moxuebridge/config/TokenGenerator.java src/main/java/io/github/neko0115/moxuebridge/security src/test/java/io/github/neko0115/moxuebridge/security
git commit -m "feat: add bearer token security primitives"
```

---

### Task 4: Implement VeinMiner deep capability discovery

**Files:**
- Create: `src/main/java/io/github/neko0115/moxuebridge/integration/PluginIntegration.java`
- Create: `src/main/java/io/github/neko0115/moxuebridge/integration/IntegrationException.java`
- Create: `src/main/java/io/github/neko0115/moxuebridge/integration/VeinMinerIntegration.java`
- Create: `src/test/java/io/github/neko0115/moxuebridge/integration/VeinMinerIntegrationTest.java`
- Create: `src/test/resources/veinminer/ores-only/settings.json`
- Create: `src/test/resources/veinminer/ores-only/groups.json`
- Create: `src/test/resources/veinminer/ores-and-logs/groups.json`

**Interfaces:**
- Produces: `PluginIntegration#supports(RuntimePluginDescriptor): boolean`
- Produces: `PluginIntegration#discoverCapabilities(RuntimePluginDescriptor): List<Capability>` throwing `IntegrationException`
- `VeinMinerIntegration` reads only VeinMiner's own data files; it never mutates them.

- [ ] **Step 1: Add the current observed VeinMiner fixtures**

`ores-only/settings.json` must contain the observed server values:

```json
{
  "cooldown": 20,
  "mustSneak": true,
  "delay": 0,
  "maxChain": 100,
  "needCorrectTool": true,
  "searchRadius": 1,
  "permissionRestricted": false,
  "mergeItemDrops": false,
  "autoUpdate": false,
  "decreaseDurability": true,
  "hungerPerBlock": 0.0,
  "miningSpeedModifier": 0.0,
  "debug": false
}
```

`ores-only/groups.json`:

```json
{
  "value": [
    {
      "name": "Ores",
      "blocks": ["#c:ores"],
      "tools": ["#minecraft:pickaxes"],
      "override": {}
    }
  ],
  "Count": 1
}
```

`ores-and-logs/groups.json` must contain the same Ores group plus:

```json
{
  "name": "Logs",
  "blocks": ["#minecraft:logs"],
  "tools": ["#minecraft:axes"],
  "override": {}
}
```

- [ ] **Step 2: Write the failing integration tests**

Tests must create a temporary VeinMiner data directory, copy fixtures into `settings.json` / `groups.json`, construct:

```java
new RuntimePluginDescriptor(
    "Veinminer",
    "2.11.2",
    true,
    tempDir,
    List.of())
```

Assertions:

```java
assertEquals(List.of("vein_mining"), ids(oresOnlyCapabilities));
assertEquals(List.of("tree_felling", "vein_mining"), sortedIds(oresAndLogsCapabilities));
```

Also test `supports(...)` case-insensitively for `VeinMiner` / `Veinminer`, disabled plugins return no capabilities, and malformed JSON throws `IntegrationException` instead of silently inventing capabilities.

- [ ] **Step 3: Run RED**

```powershell
.\gradlew.bat test --tests "*VeinMinerIntegrationTest"
```

Expected: compile failure because integration classes do not exist.

- [ ] **Step 4: Implement the integration**

`PluginIntegration`:

```java
public interface PluginIntegration {
    boolean supports(RuntimePluginDescriptor plugin);
    java.util.List<Capability> discoverCapabilities(RuntimePluginDescriptor plugin)
            throws IntegrationException;
}
```

`IntegrationException` extends `Exception` and retains the original cause.

`VeinMinerIntegration` rules:

- Return no capabilities when `plugin.enabled()` is false.
- Parse `settings.json` and `groups.json` with Gson.
- Detect ore capability only when tools contain a `pickaxes` token and blocks contain an `ores` token; group name `Ores` may substitute for the block token only when the pickaxe tool condition is still met.
- Detect tree-felling only when tools contain an `axes` token and blocks contain `logs`, `_log`, or `stem`; group name `Logs` may substitute for the block token only when the axe tool condition is still met.
- Build `usage.human` from `mustSneak` and `needCorrectTool` rather than hard-coding Shift unconditionally.
- Put `max_chain`, `correct_tool_required`, `search_radius`, and `decrease_durability` in capability constraints.
- Use provenance `integration`.
- Convert I/O and JSON parse failures into `IntegrationException` containing the source filename but never file contents.

Expected `vein_mining` capability for the current fixture:

```java
new Capability(
    "vein_mining",
    "????丹",
    "銝甈⊥?????蝷衣?孵?",
    true,
    new CapabilitySource("VeinMiner", "2.11.2", "integration"),
    new CapabilityUsage("sneak_and_break", "頩脖?銝虫蝙?冽迤蝣箇????祆????丹??),
    Map.of(
        "max_chain", 100,
        "correct_tool_required", true,
        "search_radius", 1,
        "decrease_durability", true))
```

- [ ] **Step 5: Run GREEN**

```powershell
.\gradlew.bat test --tests "*VeinMinerIntegrationTest"
```

Expected: PASS for Ores-only, Ores+Logs, case-insensitive support, disabled plugin, and malformed JSON behavior.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/io/github/neko0115/moxuebridge/integration src/test/java/io/github/neko0115/moxuebridge/integration src/test/resources/veinminer
git commit -m "feat: discover VeinMiner capabilities"
```

---

### Task 5: Build runtime plugin catalog and normalized registry snapshots

**Files:**
- Create: `src/main/java/io/github/neko0115/moxuebridge/discovery/PluginCatalog.java`
- Create: `src/main/java/io/github/neko0115/moxuebridge/discovery/BukkitPluginCatalog.java`
- Create: `src/main/java/io/github/neko0115/moxuebridge/discovery/RegistryBuilder.java`
- Create: `src/test/java/io/github/neko0115/moxuebridge/discovery/RegistryBuilderTest.java`

**Interfaces:**
- Produces: `PluginCatalog#snapshot(): List<RuntimePluginDescriptor>`
- Produces: `RegistryBuilder#build(): BridgeSnapshot`
- Consumes: `List<PluginIntegration>` and current Paper/Bukkit runtime metadata.

- [ ] **Step 1: Write failing registry-builder tests with a fake catalog**

Cover these cases:

1. Enabled VeinMiner + matching fake integration => plugin `integrated=true`, capability included.
2. Disabled VeinMiner => capability omitted.
3. Unknown enabled plugin with command metadata => plugin is reported with command metadata but no invented deep capability.
4. Integration throws `IntegrationException` => snapshot still builds and contains plugin metadata, but no capability from the failed integration.

Use deterministic `Supplier<String>` timestamp and `Supplier<BridgeStatus>` inputs so output can be asserted exactly.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat test --tests "*RegistryBuilderTest"
```

Expected: compile failure because discovery classes do not exist.

- [ ] **Step 3: Implement `PluginCatalog` and `RegistryBuilder`**

`PluginCatalog`:

```java
public interface PluginCatalog {
    java.util.List<RuntimePluginDescriptor> snapshot();
}
```

`RegistryBuilder` constructor:

```java
public RegistryBuilder(
        PluginCatalog catalog,
        java.util.List<PluginIntegration> integrations,
        java.util.function.Supplier<BridgeStatus> statusSupplier,
        java.util.function.Supplier<String> timestampSupplier,
        java.util.logging.Logger logger)
```

`build()` must:

- call catalog once;
- determine `integrated` by whether any integration supports a plugin;
- run deep integrations only for enabled plugins;
- catch `IntegrationException`, log only plugin name + exception message, and continue;
- sort plugin records by case-insensitive plugin name;
- sort capabilities by stable `id`;
- return one immutable `BridgeSnapshot`.

- [ ] **Step 4: Implement Paper/Bukkit runtime catalog adapter**

`BukkitPluginCatalog` must only run on the Paper main thread. It uses `PluginManager#getPlugins()` as the authoritative plugin list.

For each plugin:

- `name`: `plugin.getName()`
- `version`: `plugin.getPluginMeta().getVersion()`
- `enabled`: `plugin.isEnabled()`
- `dataFolder`: `plugin.getDataFolder().toPath()`
- commands: parse trusted plugin-declared commands with `PluginCommandYamlParser.parse(plugin)` and map command name, description, usage, permission, aliases into `CommandInfo`.

If command parsing fails for one plugin, log the plugin name and continue with an empty command list; do not fail the whole snapshot.

- [ ] **Step 5: Run GREEN**

```powershell
.\gradlew.bat test --tests "*RegistryBuilderTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/io/github/neko0115/moxuebridge/discovery src/test/java/io/github/neko0115/moxuebridge/discovery
git commit -m "feat: build runtime capability snapshots"
```

---

### Task 6: Add authenticated read-only HTTP API v1

**Files:**
- Create: `src/main/java/io/github/neko0115/moxuebridge/api/BridgeHttpServer.java`
- Create: `src/test/java/io/github/neko0115/moxuebridge/api/BridgeHttpServerTest.java`

**Interfaces:**
- Produces: `BridgeHttpServer#start(): void`
- Produces: `BridgeHttpServer#stop(): void`
- Produces: `BridgeHttpServer#boundPort(): int`
- Consumes: `CapabilityRegistry`, `BearerTokenValidator`, bind address, port, logger.

- [ ] **Step 1: Write failing end-to-end HTTP tests on loopback port 0**

Use JDK `HttpClient` against a real `HttpServer` bound to `127.0.0.1:0`.

Seed registry with one plugin and one capability. Test:

- GET `/api/v1/status` without token => `401`.
- GET `/api/v1/status` with `Bearer test-token` => `200`, JSON contains `bridge`, `bridge_version`, `minecraft`, `server_software`, `online`.
- GET `/api/v1/plugins` => `200`, contains seeded plugin.
- GET `/api/v1/capabilities` => `200`, contains seeded capability.
- POST `/api/v1/status` => `405`.
- GET `/api/v1/nope` => `404`.
- All JSON responses include `Content-Type: application/json; charset=utf-8`.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat test --tests "*BridgeHttpServerTest"
```

Expected: compile failure because `BridgeHttpServer` does not exist.

- [ ] **Step 3: Implement the HTTP server**

Use:

```java
com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(bindAddress, port), 0)
```

Use exactly one Gson instance:

```java
new com.google.gson.GsonBuilder()
    .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .disableHtmlEscaping()
    .create();
```

Rules:

- Authenticate before routing `/api/v1/*`.
- Allow only `GET`.
- `/status` serializes `registry.snapshot().status()`.
- `/plugins` serializes `registry.snapshot().plugins()`.
- `/capabilities` serializes `registry.snapshot().capabilities()`.
- API worker threads never call Bukkit/Paper APIs.
- Use a two-thread daemon executor named `MoxueBridge-HTTP-*`.
- `stop()` calls `server.stop(0)` and shuts down the executor.
- `500` responses use `{"error":"internal_error"}` and log stack traces locally without returning them over the network.

- [ ] **Step 4: Run GREEN**

```powershell
.\gradlew.bat test --tests "*BridgeHttpServerTest"
```

Expected: all HTTP contract tests PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/io/github/neko0115/moxuebridge/api src/test/java/io/github/neko0115/moxuebridge/api
git commit -m "feat: add authenticated capability API"
```

---

### Task 7: Wire configuration, lifecycle refresh, and plugin startup/shutdown

**Files:**
- Create: `src/main/java/io/github/neko0115/moxuebridge/config/BridgeConfiguration.java`
- Create: `src/main/java/io/github/neko0115/moxuebridge/lifecycle/PluginLifecycleListener.java`
- Modify: `src/main/java/io/github/neko0115/moxuebridge/MoxueBridgePlugin.java`
- Create: `src/test/java/io/github/neko0115/moxuebridge/config/BridgeConfigurationTest.java`

**Interfaces:**
- Produces: `BridgeConfiguration(String bindAddress, int port, String token)`
- Produces: `BridgeConfiguration.resolve(String, int, String, Supplier<String>)`
- Lifecycle refresh callback rebuilds then atomically replaces the registry snapshot.

- [ ] **Step 1: Write failing pure configuration tests**

Target static API:

```java
BridgeConfiguration.resolve(bindAddress, port, token, tokenSupplier)
```

Tests:

```java
assertEquals(
    "generated-token",
    BridgeConfiguration.resolve("0.0.0.0", 8766, "", () -> "generated-token").token());

assertEquals(
    "existing-token",
    BridgeConfiguration.resolve("0.0.0.0", 8766, "existing-token", () -> "unused").token());

assertThrows(IllegalArgumentException.class,
    () -> BridgeConfiguration.resolve("", 8766, "x", () -> "unused"));
assertThrows(IllegalArgumentException.class,
    () -> BridgeConfiguration.resolve("0.0.0.0", 0, "x", () -> "unused"));
assertThrows(IllegalArgumentException.class,
    () -> BridgeConfiguration.resolve("0.0.0.0", 65536, "x", () -> "unused"));
```

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat test --tests "*BridgeConfigurationTest"
```

Expected: compile failure because `BridgeConfiguration` does not exist.

- [ ] **Step 3: Implement configuration resolution and runtime persistence**

`BridgeConfiguration.resolve(...)` trims and validates bind address, validates port `1..65535`, preserves a nonblank token, and obtains a generated token from the supplier only when the configured token is blank.

In `MoxueBridgePlugin#onEnable()`:

1. `saveDefaultConfig()`.
2. Read `http.bind`, `http.port`, `security.token`.
3. Resolve through `BridgeConfiguration.resolve(..., tokenGenerator::generate)`.
4. If the original token was blank, write only the generated token to this plugin's runtime `config.yml`, call `saveConfig()`, and log: `Generated API token; retrieve it from plugins/MoxueBridge/config.yml`.
5. Never log the token value.

- [ ] **Step 4: Implement registry refresh wiring**

`MoxueBridgePlugin` must create:

```text
BukkitPluginCatalog
VeinMinerIntegration
RegistryBuilder
CapabilityRegistry
BearerTokenValidator
BridgeHttpServer
```

Build the initial snapshot on the server thread before starting HTTP.

Add `PluginLifecycleListener` for `PluginEnableEvent` and `PluginDisableEvent`. Ignore lifecycle events for MoxueBridge itself. For other plugins, schedule a one-tick-later main-thread rebuild using `Bukkit.getScheduler().runTask(plugin, refresher)` so command/plugin state has settled before snapshotting.

- [ ] **Step 5: Implement clean shutdown**

`onDisable()` must stop the HTTP server before the plugin finishes disabling. It must be safe when startup failed before HTTP initialization.

If HTTP bind/start fails during `onEnable()`, log a severe error and disable MoxueBridge rather than leaving a misleading half-enabled bridge.

- [ ] **Step 6: Run the complete unit test suite**

```powershell
.\gradlew.bat clean test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/io/github/neko0115/moxuebridge/config src/main/java/io/github/neko0115/moxuebridge/lifecycle src/main/java/io/github/neko0115/moxuebridge/MoxueBridgePlugin.java src/test/java/io/github/neko0115/moxuebridge/config
git commit -m "feat: wire bridge lifecycle and runtime config"
```

---

### Task 8: Add repository hygiene, CI, and operator documentation

**Files:**
- Create: `.gitignore`
- Create: `.github/workflows/ci.yml`
- Create: `README.md`

**Interfaces:**
- CI contract: every push/PR builds and runs tests on Java 21.
- Operator contract: README documents build, deploy, token retrieval, API smoke tests, and LAN firewall rule.

- [ ] **Step 1: Add `.gitignore`**

```gitignore
.gradle/
build/
.idea/
*.iml
out/
run/
*.log
```

Do not ignore source `config.yml`; it contains only empty/default secret fields. Runtime `D:\MC_AI_Server\plugins\MoxueBridge\config.yml` is outside the repo and must never be copied into Git.

- [ ] **Step 2: Add GitHub Actions CI**

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
  pull_request:

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew clean test build
```

- [ ] **Step 3: Write README operator workflow**

README must document exactly:

```powershell
cd D:\MoxueBridge
.\gradlew.bat clean test build
Copy-Item ".\build\libs\MoxueBridge-0.1.0.jar" "D:\MC_AI_Server\plugins\MoxueBridge.jar" -Force
```

Then a clean Paper restart, not `/reload`.

Document token retrieval from:

```text
D:\MC_AI_Server\plugins\MoxueBridge\config.yml
```

Document a Private-LAN-only firewall rule:

```powershell
New-NetFirewallRule `
  -DisplayName "MoxueBridge API 8766 (Private LAN)" `
  -Direction Inbound `
  -Protocol TCP `
  -LocalPort 8766 `
  -Action Allow `
  -Profile Private `
  -RemoteAddress LocalSubnet
```

Explicitly state that Playit must not forward 8766.

- [ ] **Step 4: Run full local verification**

```powershell
.\gradlew.bat clean test build
git status
```

Expected: tests/build PASS; only intended documentation/CI changes remain before commit.

- [ ] **Step 5: Commit**

```powershell
git add .gitignore .github README.md
git commit -m "docs: add CI and operator guide"
```

---

### Task 9: Deploy to the actual Paper server and run acceptance smoke tests

**Files:**
- Deploy artifact only: `D:\MC_AI_Server\plugins\MoxueBridge.jar`
- Runtime generated: `D:\MC_AI_Server\plugins\MoxueBridge\config.yml`
- Runtime VeinMiner test config: `D:\MC_AI_Server\plugins\Veinminer\groups.json`
- No source files are edited in this task unless a smoke test exposes a defect.

**Interfaces:**
- Validates the complete Paper runtime + HTTP API + VeinMiner integration on the target server.

- [ ] **Step 1: Build and deploy**

```powershell
cd D:\MoxueBridge
.\gradlew.bat clean test build
Copy-Item ".\build\libs\MoxueBridge-0.1.0.jar" "D:\MC_AI_Server\plugins\MoxueBridge.jar" -Force
```

Stop Paper cleanly with `stop`, then start it normally.

- [ ] **Step 2: Verify Paper loaded the plugin without errors**

```powershell
Select-String `
  -Path "D:\MC_AI_Server\logs\latest.log" `
  -Pattern "MoxueBridge|ERROR|Exception" |
Select-Object -Last 80
```

Expected: MoxueBridge enable line, HTTP bind confirmation, no MoxueBridge exception.

In the Paper console:

```text
plugins
```

Expected: MoxueBridge and VeinMiner are enabled.

- [ ] **Step 3: Retrieve token without printing it into shared logs**

Inspect locally:

```powershell
notepad "D:\MC_AI_Server\plugins\MoxueBridge\config.yml"
```

Copy the generated token into a temporary PowerShell variable for smoke testing; do not commit or paste the token into GitHub/chat logs.

- [ ] **Step 4: Test authentication and status locally**

With `$token` set only in the local PowerShell session:

```powershell
Invoke-WebRequest "http://127.0.0.1:8766/api/v1/status" -UseBasicParsing
```

Expected: HTTP 401.

```powershell
Invoke-RestMethod `
  "http://127.0.0.1:8766/api/v1/status" `
  -Headers @{ Authorization = "Bearer $token" }
```

Expected JSON fields: `bridge=MoxueBridge`, `bridge_version=0.1.0`, `minecraft=1.21.1`, `server_software=Paper`, `online=true`.

- [ ] **Step 5: Verify Ores-only baseline capability**

```powershell
$caps = Invoke-RestMethod `
  "http://127.0.0.1:8766/api/v1/capabilities" `
  -Headers @{ Authorization = "Bearer $token" }

$caps | ConvertTo-Json -Depth 10
```

Expected before adding Logs:

```text
vein_mining present
tree_felling absent
max_chain = 100
correct_tool_required = true
```

- [ ] **Step 6: Verify runtime plugin metadata**

```powershell
Invoke-RestMethod `
  "http://127.0.0.1:8766/api/v1/plugins" `
  -Headers @{ Authorization = "Bearer $token" } |
ConvertTo-Json -Depth 10
```

Expected: VeinMiner is `enabled=true` and `integrated=true`; unknown plugins remain visible without fabricated deep capabilities.

- [ ] **Step 7: Add LAN firewall rule and test from the DC_BOT desktop**

On the server laptop, as Administrator:

```powershell
New-NetFirewallRule `
  -DisplayName "MoxueBridge API 8766 (Private LAN)" `
  -Direction Inbound `
  -Protocol TCP `
  -LocalPort 8766 `
  -Action Allow `
  -Profile Private `
  -RemoteAddress LocalSubnet
```

The current known server-laptop LAN IPv4 is `192.168.50.159`. Confirm it on the server before testing:

```powershell
Get-NetIPAddress -AddressFamily IPv4 |
Where-Object {
    $_.IPAddress -notlike "127.*" -and
    $_.IPAddress -notlike "169.254.*"
} |
Select-Object InterfaceAlias,IPAddress
```

From the DC_BOT desktop, if the confirmed address is still `192.168.50.159`:

```powershell
Test-NetConnection 192.168.50.159 -Port 8766
```

If the confirmation command shows a different current LAN address, use that confirmed address instead. Then issue the authenticated status request from the desktop using the same token stored only in that local PowerShell session.

Expected: TCP succeeds and authenticated status returns 200; unauthenticated request returns 401.

- [ ] **Step 8: Validate tree-felling discovery as the second real capability case**

Use VeinMiner itself to create the Logs group instead of manually serializing `groups.json`.

In the Paper console:

```text
veinminer groups create Logs
veinminer groups edit Logs add-block #minecraft:logs
veinminer groups edit Logs add-tool #minecraft:axes
```

VeinMiner 2.11.2 stores `groups.json` as a top-level JSON array, for example:

```json
[
  {
    "name": "Ores",
    "blocks": ["#c:ores"],
    "tools": ["#minecraft:pickaxes"],
    "override": {}
  },
  {
    "name": "Logs",
    "blocks": ["#minecraft:logs"],
    "tools": ["#minecraft:axes"],
    "override": {}
  }
]
```

Do not wrap the array in `{ "value": [...] }`.

Stop and restart Paper cleanly after changing the VeinMiner groups. A group configuration edit does not itself emit a plugin enable/disable lifecycle event, so the clean restart guarantees MoxueBridge rebuilds the initial capability snapshot from the updated configuration.

Repeat `/api/v1/capabilities`.

Expected without changing MoxueBridge API or any DC_BOT code:

```text
vein_mining present
tree_felling present
```

Also verify in-game that sneak + axe actually performs the configured tree-felling behavior before treating the capability as user-ready.

- [ ] **Step 9: Final full verification and push**

```powershell
cd D:\MoxueBridge
.\gradlew.bat clean test build
git status
git log --oneline -10
git push origin main
```

Expected: all tests PASS, working tree clean, all implementation commits pushed.

---

## Plan Self-Review

### Spec coverage

- Paper 1.21.1 / Java 21 lifecycle: Tasks 1 and 7.
- Runtime-authoritative plugin state: Task 5.
- Generic trustworthy command metadata: Task 5.
- Normalized immutable capability registry: Tasks 2 and 5.
- VeinMiner `vein_mining` / `tree_felling`: Task 4 and Task 9.
- Read-only authenticated HTTP API: Tasks 3, 6, and 7.
- LAN deployment and Playit separation: Tasks 8 and 9.
- Public repository secret hygiene: Tasks 3, 7, and 8.
- Unknown-plugin no-fabrication rule: Task 5.
- Runtime smoke acceptance: Task 9.
- DC_BOT stale/current/unavailable client behavior is intentionally excluded from this plan and will be implemented as a separate plan in the DC_BOT repository after MoxueBridge v0.1.0 passes its server-side acceptance tests.

### Type consistency

The stable boundary used by later tasks is `CapabilityRegistry -> BridgeSnapshot -> {status, plugins, capabilities}`. Integration code receives `RuntimePluginDescriptor` rather than raw Bukkit `Plugin`, keeping VeinMiner parsing testable without a running Paper server. HTTP code depends only on `CapabilityRegistry`, so HTTP worker threads do not touch Bukkit state. Gson's `LOWER_CASE_WITH_UNDERSCORES` policy converts Java record members such as `bridgeVersion`, `serverSoftware`, and `generatedAt` into the exact v1 JSON names defined by the spec.

### Security consistency

The plan never commits a live token, never logs a token value, requires authentication on all v1 endpoints, uses constant-time token comparison, keeps the API read-only, and documents a Private-LAN firewall rule while explicitly excluding Playit forwarding.
