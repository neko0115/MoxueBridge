# MoxueBridge Architecture Design

Date: 2026-09-17
Status: Proposed for implementation
Target: Paper 1.21.1
Initial consumer: DC_BOT
Future consumer: NC_AI_Player

## 1. Purpose

MoxueBridge is a Paper plugin that exposes a structured, read-only description of the Minecraft server's runtime capabilities to external Moxue clients.

The primary goal is not merely to list installed plugins. MoxueBridge should translate runtime plugin state and configuration into player-facing capabilities such as:

- vein mining
- tree felling
- homes / teleport commands
- rollback / lookup tools
- other future server features

DC_BOT can then include those capabilities in Moxue's context so that Moxue can accurately tell players which server features exist and how to use them.

## 2. Deployment topology

The Minecraft server and MoxueBridge run on the server laptop. DC_BOT runs on a separate desktop computer on the same LAN.

```text
Server laptop
D:\MC_AI_Server
└─ plugins
   └─ MoxueBridge.jar

D:\MoxueBridge
└─ Git working copy / source / tests / Gradle build

                 LAN
                  │
                  ▼
Desktop computer
F:\DC_BOT
└─ MinecraftBridgeClient
```

The source repository is independent from the Paper server runtime directory. A build artifact is copied from `D:\MoxueBridge` into `D:\MC_AI_Server\plugins` for deployment.

## 3. Scope of version 0.1.0

Version 0.1.0 is read-only.

Allowed:

- report bridge status
- report Minecraft / Paper runtime information
- enumerate runtime-loaded plugins
- report whether plugins are enabled
- expose generic plugin metadata and registered commands where trustworthy
- expose normalized capabilities
- deeply inspect VeinMiner configuration through a dedicated integration

Explicitly out of scope:

- executing console commands
- granting OP
- changing permissions
- kicking or banning players
- giving items
- teleporting players
- changing world state
- writing plugin configuration
- exposing the API through Playit

Future write/action APIs require a separate authenticated authorization design and audit logging.

## 4. Network and security model

Because DC_BOT is on a different computer, the bridge cannot be localhost-only.

MoxueBridge will bind an HTTP server to a configurable LAN-facing address, initially:

```text
0.0.0.0:8766
```

Security requirements:

1. Every API request except an optional health probe must require a bearer token.
2. The bearer token must never be committed to Git.
3. The runtime token belongs in the Paper plugin data directory, for example:
   `D:\MC_AI_Server\plugins\MoxueBridge\config.yml`.
4. A first-run token may be generated automatically using a cryptographically secure random generator.
5. Windows Firewall should restrict TCP 8766 to the private LAN or, preferably, the DC_BOT desktop IP.
6. Playit must expose only the Minecraft gameplay tunnel, not the MoxueBridge API.
7. Logs must never print the full bearer token.

Repository examples must contain placeholders only.

## 5. Core architecture

```text
Paper runtime
   │
   ├─ PluginManager runtime state
   ├─ registered commands / metadata
   └─ plugin configuration
            │
            ▼
     Discovery pipeline
            │
   ┌────────┼────────┐
   │        │        │
Runtime   Generic   Deep
Plugin    Metadata  Integration
Discovery Discovery Adapters
   │        │        │
   └────────┼────────┘
            ▼
     Capability Registry
            │
            ▼
      Read-only HTTP API
            │
            ▼
      DC_BOT bridge client
            │
            ▼
 Minecraft context builder
            │
            ▼
           Moxue
```

The Capability Registry is the stable boundary. Consumers should not need to understand plugin-specific configuration formats.

## 6. Plugin registry

MoxueBridge must use Paper/Bukkit runtime state as the authoritative source for whether a plugin is actually loaded and enabled.

A plugin record should include at least:

```json
{
  "name": "VeinMiner",
  "version": "2.11.2",
  "enabled": true,
  "integrated": true
}
```

A JAR merely existing in the plugins directory is not enough to claim that a feature is available.

Unknown plugins are still reported as runtime plugins, but MoxueBridge must not invent capabilities that cannot be supported by metadata, commands, a manual manifest, or a dedicated integration.

## 7. Capability model

Capabilities describe what a player can actually do.

Canonical shape:

```json
{
  "id": "vein_mining",
  "name": "連鎖挖礦",
  "description": "一次挖掘相連的礦物方塊",
  "available": true,
  "source": {
    "plugin": "VeinMiner",
    "version": "2.11.2",
    "provenance": "integration"
  },
  "usage": {
    "trigger": "sneak_and_break",
    "human": "蹲下並使用正確的十字鎬挖掘礦物"
  },
  "constraints": {
    "max_chain": 100,
    "correct_tool_required": true
  }
}
```

The schema may evolve, but capability IDs should remain stable where possible because consumers may refer to them programmatically.

## 8. Capability discovery levels

MoxueBridge uses layered discovery.

### Level 1: runtime plugin discovery

Authoritative facts supplied by Paper/Bukkit:

- plugin name
- plugin version
- enabled state
- plugin metadata that Paper exposes

### Level 2: generic metadata / command discovery

When a plugin registers well-described commands, MoxueBridge may expose those commands and their published descriptions as generic capabilities or command features.

This information must remain clearly sourced from plugin metadata/runtime command registration. The bridge must not infer undocumented behavior from a plugin name alone.

### Level 3: deep integrations

Dedicated integrations understand important plugin-specific configuration and translate it into normalized player capabilities.

Initial integration:

```text
VeinMinerIntegration
```

Future examples:

```text
CoreProtectIntegration
EssentialsXIntegration
LuckPermsIntegration
```

### Optional Level 4: manual capability manifest

A future `capabilities.yml` may provide trusted manual descriptions for plugins whose metadata is insufficient and that do not justify a Java integration.

Manual entries must be marked with provenance `manual_manifest`.

## 9. Integration interface

The implementation should keep integrations isolated behind a small interface conceptually equivalent to:

```java
public interface PluginIntegration {
    String pluginName();
    boolean supports(Plugin plugin);
    List<Capability> discoverCapabilities(Plugin plugin);
}
```

Integrations must not directly control HTTP serialization or consumer-specific prompt formatting.

Each integration should be independently testable.

## 10. VeinMiner integration

VeinMiner is the first acceptance integration.

Current observed configuration on the target server:

`settings.json`:

- `mustSneak = true`
- `maxChain = 100`
- `needCorrectTool = true`
- `searchRadius = 1`
- `decreaseDurability = true`

`groups.json` currently contains exactly one group:

```text
Ores
blocks: #c:ores
tools: #minecraft:pickaxes
```

Therefore the initial expected capability set is:

```text
vein_mining = available
tree_felling = absent
```

When a Logs group is later added using appropriate log/stem blocks and axes, the same integration must discover:

```text
vein_mining = available
tree_felling = available
```

No DC_BOT code or static system prompt should need to change for that transition.

## 11. Registry refresh

The registry is built during MoxueBridge startup after plugin state is available.

It should also refresh on relevant plugin enable/disable lifecycle events where safe.

Normal plugin installation and upgrades should still be performed through a clean Paper server restart rather than relying on `/reload`.

A registry snapshot should be immutable to API readers while a refresh is occurring, so clients never observe a partially-built state.

## 12. HTTP API v1

Initial endpoints:

### `GET /api/v1/status`

Example:

```json
{
  "bridge": "MoxueBridge",
  "bridge_version": "0.1.0",
  "minecraft": "1.21.1",
  "server_software": "Paper",
  "online": true
}
```

### `GET /api/v1/plugins`

Returns runtime plugin records.

### `GET /api/v1/capabilities`

Returns normalized capabilities.

All normal API responses should use JSON and a stable UTF-8 encoding.

Suggested error behavior:

- `401` missing/invalid bearer token
- `404` unknown endpoint
- `405` unsupported method
- `500` internal bridge failure, without leaking secrets or raw stack traces to the network response

## 13. DC_BOT integration

DC_BOT is the initial consumer, but it must remain loosely coupled to MoxueBridge.

A small `MinecraftBridgeClient` should:

- call the v1 API
- send the bearer token
- use short HTTP timeouts
- maintain a last-known-good snapshot
- expose freshness information to the context builder

Suggested behavior:

- cache successful capability results for approximately 30 seconds
- refresh lazily when the cache expires
- use approximately 0.5-1.0 second network timeout so Minecraft cannot stall unrelated Discord conversation

The Moxue context should contain a compact human-readable summary rather than raw plugin JSON.

Example:

```text
[Minecraft Server Capabilities]
Server: Paper 1.21.1
Status: online
Capability data: current

Available player features:
- 連鎖挖礦 [vein_mining]
  使用方式：蹲下並使用正確十字鎬挖掘相連礦物
  最大連鎖：100
  Provider: VeinMiner 2.11.2
```

## 14. Failure and freshness semantics

Minecraft availability must never become a hard dependency for normal DC_BOT conversation.

Client states:

### Current

Bridge contacted successfully and the snapshot is fresh.

### Stale

Bridge is currently unavailable but a last-known-good snapshot exists.

Moxue may describe such information as the most recently synchronized server configuration, but must not claim it is currently verified.

### Unavailable

No successful snapshot exists. Moxue should say Minecraft capability information is unavailable instead of guessing.

## 15. Public repository requirements

The repository is public.

Never commit:

- bearer tokens
- private server credentials
- passwords
- unnecessary private network identifiers
- generated runtime configuration containing secrets

`.gitignore` and documentation must reinforce this boundary.

## 16. Version 0.1.0 acceptance criteria

1. Paper 1.21.1 loads `MoxueBridge.jar` successfully.
2. `/api/v1/status` returns authenticated runtime status.
3. `/api/v1/plugins` reports VeinMiner as enabled when Paper has actually enabled it.
4. With the current VeinMiner `Ores` group only, `/api/v1/capabilities` includes `vein_mining` and does not include `tree_felling`.
5. After adding a valid Logs group and restarting/reloading the integration state safely, capabilities include both `vein_mining` and `tree_felling` without modifying DC_BOT or its static prompt.
6. A new plugin with trustworthy registered command metadata can be surfaced generically without a dedicated integration.
7. Unknown plugins do not cause fabricated player capabilities.
8. Requests without a valid token are rejected.
9. The API is not exposed through Playit.
10. DC_BOT continues normal conversation if MoxueBridge or Minecraft is offline.
11. DC_BOT distinguishes current, stale, and unavailable capability data.

## 17. Testing strategy

Implementation should be test-driven.

Initial RED/GREEN sequence:

1. Capability registry starts empty.
2. VeinMiner integration given the current settings/groups fixture discovers `vein_mining` only.
3. Adding a Logs fixture discovers `tree_felling` without changing integration API consumers.
4. Disabled runtime plugin does not produce available capabilities.
5. Unknown plugin produces plugin metadata but no invented deep capability.
6. Authentication rejects missing/incorrect tokens.
7. HTTP JSON output matches the v1 contract.
8. Manual Paper smoke test confirms the built JAR loads on the actual server.

## 18. Build and deployment workflow

Primary working copy on the server laptop:

```text
D:\MoxueBridge
```

Typical flow:

```text
edit / test
   ↓
Gradle build
   ↓
build/libs/MoxueBridge-<version>.jar
   ↓
copy to D:\MC_AI_Server\plugins\MoxueBridge.jar
   ↓
clean Paper restart
   ↓
runtime/API smoke test
```

GitHub is the canonical source history; the Paper `plugins` directory is a deployment target, not a source tree.

## 19. Future extensions

Not part of version 0.1.0, but the design intentionally leaves room for:

- NC_AI_Player as a second API consumer
- player presence/status information
- event streaming through WebSocket or SSE
- authenticated action APIs with explicit allowlists and audit logs
- richer plugin integrations
- capability manifests
- runtime observability / metrics

These extensions must preserve the read-only v1 contract unless a versioned API change is deliberately introduced.
