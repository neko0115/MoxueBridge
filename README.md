# MoxueBridge

MoxueBridge is a read-only Paper plugin that exposes Minecraft server runtime plugin and capability information to Moxue clients such as DC_BOT.

## Requirements

- Minecraft 1.21.1
- Paper
- Java 21
- MoxueBridge 0.1.0

## Current capabilities

MoxueBridge currently provides:

- Runtime plugin discovery
- Plugin enabled/version metadata
- Plugin command metadata
- VeinMiner deep integration
- `vein_mining` capability discovery
- `tree_felling` capability discovery when a compatible Logs group exists
- Authenticated read-only HTTP API
- Runtime capability refresh after plugin enable/disable events

## Build

On the server laptop:

```powershell
cd D:\MoxueBridge
.\gradlew.bat clean test build
```

The JAR is produced at:

```text
build\libs\MoxueBridge-0.1.0.jar
```

## Deploy

Stop the Paper server cleanly before replacing the plugin.

Then copy the built JAR:

```powershell
Copy-Item `
  ".\build\libs\MoxueBridge-0.1.0.jar" `
  "D:\MC_AI_Server\plugins\MoxueBridge.jar" `
  -Force
```

Restart Paper after deployment.

## Configuration

Runtime configuration is stored outside this repository at:

```text
D:\MC_AI_Server\plugins\MoxueBridge\config.yml
```

Default configuration:

```yaml
http:
  bind: "0.0.0.0"
  port: 8766

security:
  token: ""
```

If `security.token` is blank on first startup, MoxueBridge generates a secure token and writes it to the runtime configuration.

The token must never be committed to Git or shared publicly.

MoxueBridge logs only:

```text
Generated API token; retrieve it from plugins/MoxueBridge/config.yml
```

It does not log the token value.

## HTTP API

All `/api/v1/*` requests require:

```http
Authorization: Bearer <token>
```

Available endpoints:

```text
GET /api/v1/status
GET /api/v1/plugins
GET /api/v1/capabilities
```

Example local request:

```powershell
$token = "<token from runtime config>"

Invoke-RestMethod `
  "http://127.0.0.1:8766/api/v1/status" `
  -Headers @{
      Authorization = "Bearer $token"
  }
```

Requests without a valid token return HTTP 401.

## LAN access

The API is intended only for communication between the Minecraft server laptop and trusted Moxue clients on the LAN.

On the server laptop, run PowerShell as Administrator:

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

For tighter security, restrict the firewall rule to the DC_BOT desktop IP instead of the entire local subnet.

Do **not** expose TCP port `8766` through Playit.

Playit should continue to expose only the Minecraft gameplay port.

## VeinMiner integration

MoxueBridge reads VeinMiner runtime configuration from the plugin data directory.

With the current server configuration:

```text
Ores
blocks: #c:ores
tools: #minecraft:pickaxes
```

and:

```text
mustSneak = true
maxChain = 100
needCorrectTool = true
```

MoxueBridge exposes:

```text
vein_mining
```

If a compatible `Logs` group using axes exists, it additionally exposes:

```text
tree_felling
```

For each VeinMiner-backed capability, MoxueBridge resolves the effective
group settings (global settings overridden by the group's `override` object)
and exposes semantic constraints including:

```text
max_chain
correct_tool_required
must_sneak
same_block_only
```

`same_block_only` maps directly from VeinMiner's `separateGroupMining`.
When it is `false`, blocks inside the same VeinMiner group may chain together;
when it is `true`, only the exact mined block type chains. Missing
`separateGroupMining` is treated as `false`, matching VeinMiner's default and
failing closed for MC_AI_Player multi-block automation.

## Security model

Version 0.1.0 is read-only.

It does not provide API operations for:

- Console command execution
- OP changes
- Permission changes
- Kicking or banning players
- Giving items
- Teleportation
- World modification
- Modifying third-party plugin configuration

Future write/action APIs require a separate authorization and audit design.

## Development

Run all tests:

```powershell
.\gradlew.bat clean test
```

Run the complete build:

```powershell
.\gradlew.bat clean test build
```