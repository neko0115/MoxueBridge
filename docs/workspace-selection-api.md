# Workspace selection endpoint

The workspace selection API is observation-only.

## Wand

- material: `minecraft:stick`
- visible display name: `墨雪設定棍`
- optional persistent marker:
  `moxuebridge:workspace_wand = "v1"`

Current v1 behavior:

- left-click block -> point A
- right-click block -> point B
- main hand only
- wand interaction is cancelled so the selected block is not also used
- A and B must belong to the same world/dimension
- once both exist, the newest complete selection is exposed

## Endpoint

`GET /api/v1/workspace-selections`

Uses the same bearer token as the other MoxueBridge endpoints.

Wire shape uses Gson lower_case_with_underscores:

```json
{
  "version": 1,
  "generated_at": 1234567890,
  "selections": [
    {
      "id": "uuid",
      "generation": 1,
      "dimension": "overworld",
      "player_id": "uuid",
      "player_name": "Boss",
      "point_a": { "x": 0, "y": 64, "z": 0 },
      "point_b": { "x": 8, "y": 64, "z": 8 },
      "selected_at": 1234567800
    }
  ]
}
```

MC_AI_Player injects its own server/world connection key when mapping the trusted Bridge observation into its domain-level `WorkspaceSelection`. This avoids relying on host-name spelling inside the Paper plugin.

The endpoint never grants block breaking/placing authority.
