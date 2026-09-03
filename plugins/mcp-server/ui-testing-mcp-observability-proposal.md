# UI Testing MCP: action observations and incremental accessibility state

## Status

This document defines the version 1 UI-observation contract implemented by `intellij.uiTesting.mcpServer`.

The contract makes successful UI actions self-describing in the common case. A client receives the action target, the resulting semantic UI revision, and either a bounded semantic diff or a correlated full accessibility snapshot. Full snapshots remain available for discovery and diagnostics, but are no longer required after every action.

The accessibility tree is the semantic source of truth. Screenshots remain a separate visual artifact and are correlated with, but never used to calculate, semantic changes.

## Goals and boundaries

Version 1 provides:

- `summary`, `none`, and `full` action-observation modes;
- opaque session-local revisions and correlation-only element/window refs;
- bounded semantic diffs for the desktop or active window;
- focus, window, overlay, target-state, and generic node changes;
- privacy-aware text handling and bounded payloads;
- a settle barrier for immediate post-action UI work;
- explicit observation degradation without turning successful input dispatch into a false action failure.

Version 1 does not provide:

- opaque refs as locator inputs;
- ranked strict-locator ambiguity candidates;
- structured actionability diagnostics;
- event subscriptions;
- historical full snapshots;
- `target_subtree` diff scope;
- OCR or image-based diffs;
- FUS telemetry.

There is no implicit full-snapshot fallback. A client receives only the observation mode it requested.

## Public action contract

### Observation arguments

Every side-effecting UI tool has these optional arguments:

```json
{
  "observe": "summary",
  "sinceRevision": null,
  "settleMs": 250
}
```

| Argument | Values | Default | Behavior |
| --- | --- | --- | --- |
| `observe` | `summary`, `none`, `full` | `summary` | Selects the post-action observation. |
| `sinceRevision` | opaque revision or `null` | `null` | Overrides the summary baseline. Valid only with `summary`. |
| `settleMs` | integer milliseconds | `250` | Clamped by the server to `0..2000`. Ignored by `none`. |

Supplying `sinceRevision` with `none` or `full` is an input error detected before the action is executed.

The arguments are present on:

- `ide_click_element`;
- `ide_fill`;
- `ide_drag_to`;
- `ide_click`;
- `ide_double_click`;
- `ide_hover`;
- `ide_right_click`;
- `ide_wait_for_element`;
- `ide_type`;
- `ide_press_key`;
- `ide_scroll`;
- `ide_drag`.

### Result envelope

These tools return one structured `UiActionResult`:

```json
{
  "result": "success",
  "action": "ide_press_key",
  "target": {
    "ref": "ui-node:opaque:72",
    "role": "TEXT",
    "name": "Editor for Utils.kt",
    "className": "EditorComponentImpl",
    "windowRef": "ui-window:opaque:1"
  },
  "key": {
    "key": "ESCAPE",
    "modifiers": []
  },
  "observation": {
    "kind": "summary",
    "availability": "available",
    "revision": "ui-rev:opaque:19"
  }
}
```

Depending on the action, the envelope can contain:

- `target` for click, fill, hover, scroll, type, key, and wait-present operations;
- `source` and `destination` for drag operations;
- normalized `key` and `modifiers` for `ide_press_key`;
- `condition` and `matched` for `ide_wait_for_element`.

Nullable fields that do not apply are omitted. Actionability, locator, timeout, and dispatch failures remain MCP errors. `UiActionResult.result` currently has one wire value, `success`.

### `summary`

`summary` is the default. The coordinator captures a baseline before dispatch, executes the action, settles the resulting state, and returns a bounded semantic transition:

```json
{
  "result": "success",
  "action": "ide_right_click",
  "target": {
    "ref": "ui-node:opaque:72",
    "role": "TEXT",
    "name": "Editor for Utils.kt",
    "className": "EditorComponentImpl",
    "pathHint": "IDE frame / Utils.kt / Editor",
    "windowRef": "ui-window:opaque:1",
    "bounds": {"x": 330, "y": 120, "width": 1160, "height": 780}
  },
  "observation": {
    "kind": "summary",
    "availability": "available",
    "revision": "ui-rev:opaque:19",
    "baseline": {
      "status": "matched",
      "revision": "ui-rev:opaque:18"
    },
    "settle": {
      "status": "settled_after_change",
      "elapsedMs": 75,
      "timedOut": false
    },
    "focus": {
      "changed": true,
      "before": {
        "ref": "ui-node:opaque:72",
        "role": "TEXT",
        "name": "Editor for Utils.kt",
        "className": "EditorComponentImpl"
      },
      "after": {
        "ref": "ui-node:opaque:91",
        "role": "POPUP_MENU",
        "name": "Editor context menu",
        "className": "ActionPopupMenuImpl"
      }
    },
    "overlays": {
      "opened": [
        {
          "ref": "ui-node:opaque:91",
          "role": "POPUP_MENU",
          "name": "Editor context menu",
          "items": ["Show Context Actions", "Cut", "Copy", "Paste", "Refactor"],
          "totalItems": 17,
          "truncated": true
        }
      ],
      "closed": []
    },
    "changes": {
      "added": [],
      "removed": [],
      "updated": [],
      "addedCount": 0,
      "removedCount": 0,
      "updatedCount": 0,
      "truncated": false
    }
  }
}
```

An opened or closed overlay and its descendants are excluded from generic `changes` so the same popup is not reported twice.

If `sinceRevision` is retained, it replaces the immediate pre-action baseline. If it is unknown, foreign, expired, or evicted, the action still succeeds and the observation contains:

```json
{
  "baseline": {
    "status": "unavailable",
    "requestedRevision": "ui-rev:old-session:5"
  },
  "notices": [
    {
      "code": "baseline_unavailable",
      "message": "The requested revision is unknown or no longer retained."
    }
  ]
}
```

No semantic change is represented by the same resulting revision, an empty diff, and a `no_observable_change` notice. Successful input dispatch does not imply a semantic transition.

### `none`

`none` is the batching mode. It performs no baseline capture and no settle loop. It executes the action, performs exactly one post-action accessibility capture, and returns the resulting revision:

```json
{
  "result": "success",
  "action": "ide_press_key",
  "key": {"key": "ENTER", "modifiers": []},
  "observation": {
    "kind": "none",
    "availability": "available",
    "revision": "ui-rev:opaque:32"
  }
}
```

After several `none` actions, the client calls `ide_get_ui_diff` with the revision before the batch and the final revision. Intermediate diffs are not serialized.

### `full`

`full` captures a baseline for settle semantics, executes the action, and returns the complete settled post-action accessibility snapshot:

```json
{
  "result": "success",
  "action": "ide_press_key",
  "key": {"key": "ESCAPE", "modifiers": []},
  "observation": {
    "kind": "full",
    "availability": "available",
    "revision": "ui-rev:opaque:33",
    "settle": {
      "status": "settled_after_change",
      "elapsedMs": 75,
      "timedOut": false
    },
    "snapshot": {
      "className": "Application",
      "revision": "ui-rev:opaque:33",
      "children": []
    }
  }
}
```

`full` does not also return `changes`, `focus`, `windows`, or `overlays`.

### Wait results

A successful absent wait is no longer an opaque `null`:

```json
{
  "result": "success",
  "action": "ide_wait_for_element",
  "condition": "absent",
  "observation": {
    "kind": "summary",
    "availability": "available",
    "revision": "ui-rev:opaque:34",
    "overlays": {
      "opened": [],
      "closed": [
        {
          "ref": "ui-node:opaque:91",
          "role": "POPUP_MENU",
          "name": "Editor context menu",
          "items": [],
          "totalItems": 0,
          "truncated": false
        }
      ]
    }
  }
}
```

For `condition="present"`, `matched` contains the matched element summary.

## Opaque revisions and refs

A public revision is an opaque string such as:

```text
ui-rev:7mQv...:18
```

The client must not parse it, compare it numerically, or infer ordering from its spelling. It stores and passes the token to `sinceRevision`, `fromRevision`, or `toRevision`.

Internally, each MCP session history has a random nonce and a monotonic ordinal. A revision is allocated only when normalized semantic state changes. Repeated equivalent captures reuse the latest token. A token from another connection cannot accidentally address the current session history.

Element and window refs are similarly opaque:

```text
ui-node:<opaque-session-part>:<logical-id>
ui-window:<opaque-session-part>:<logical-id>
```

Refs correlate nodes across retained summaries and snapshots. Version 1 does not accept them as locator inputs.

The existing numeric remote-driver `UiSnapshot.revision`, node IDs, and window IDs remain internal/raw compatibility data. MCP observation revisions are separate. Numeric node/window IDs remain in existing snapshot and lookup results, while `ref`, `windowRef`, and the opaque revision are added.

## Read-only tools

### `ide_get_ui_diff`

Input:

```json
{
  "fromRevision": "ui-rev:opaque:30",
  "toRevision": "ui-rev:opaque:32",
  "scope": "desktop"
}
```

`toRevision` defaults to the latest retained revision in the current MCP session. Supported scopes are `desktop` and `active_window`.

Matched result:

```json
{
  "status": "matched",
  "scope": "desktop",
  "fromRevision": "ui-rev:opaque:30",
  "toRevision": "ui-rev:opaque:32",
  "currentRevision": "ui-rev:opaque:32",
  "focus": {"changed": false},
  "windows": {
    "activeBefore": "ui-window:opaque:1",
    "activeAfter": "ui-window:opaque:1",
    "opened": [],
    "closed": []
  },
  "overlays": {"opened": [], "closed": []},
  "changes": {
    "added": [],
    "removed": [],
    "updated": [
      {
        "element": {
          "ref": "ui-node:opaque:105",
          "role": "TEXT",
          "name": "Search Everywhere",
          "className": "SearchTextField"
        },
        "changedFields": ["text"]
      }
    ],
    "addedCount": 0,
    "removedCount": 0,
    "updatedCount": 1,
    "truncated": false
  }
}
```

Failure statuses are structured results, not implicit fallbacks:

- `baseline_unavailable`: `fromRevision` is unknown, foreign, expired, or evicted;
- `target_unavailable`: explicit `toRevision` is unavailable;
- `scope_changed`: `active_window` was requested but the logical active window differs between revisions.

### `ide_get_ui_observation_capabilities`

```json
{
  "version": 1,
  "defaultObserve": "summary",
  "modes": ["summary", "none", "full"],
  "supportsDiff": true,
  "diffScopes": ["desktop", "active_window"],
  "revisionKind": "opaque_session_local",
  "stableRefs": "correlation_only",
  "supportsHistoricalFullSnapshot": false,
  "limits": {
    "defaultSettleMs": 250,
    "maximumSettleMs": 2000,
    "changedNodesPerCategory": 20,
    "overlayItems": 10
  }
}
```

## Existing read results

| Tool | Observation correlation |
| --- | --- |
| `ide_snapshot` | Full tree has an opaque `revision`; shaped nodes/windows have refs. |
| `ide_take_screenshot` | Existing path, dimensions, scale, and origin plus correlated accessibility `revision`. |
| `ide_find_element` | Existing locator result plus `ref` and opaque `revision`. |
| `ide_get_focused_element` | Existing focused-element result plus `ref` and opaque `revision`. |
| `ide_get_windows` | Numeric IDs remain; refs and opaque `revision` are added. |
| `ide_screenshot_to_screen` | Unchanged coordinate conversion contract. |

Snapshot, window, screenshot, find, and focus correlation is serialized with UI-changing actions under one application-level mutex. Another MCP UI action cannot interleave between the relevant operation and its committed capture.

## Normalized state and matching

The normalizer stores a bounded semantic representation rather than the raw tree. It:

- trims and collapses whitespace;
- truncates stored names, descriptions, labels, and path hints;
- sorts states and actions;
- ignores raw capture revision, caret position, and selection geometry;
- suppresses bounds differences up to two logical pixels;
- excludes password text, text lists, values, fingerprints, and lengths;
- stores editable text as a session-salted SHA-256 fingerprint and length rather than editor contents.

Logical matching prefers exact raw node IDs. Recreated components are matched conservatively by window ref, role, component/Java class, normalized name, parent ref, and sibling ordinal. A fallback is accepted only when it identifies one candidate; otherwise the transition is represented as remove/add.

The public ref is allocated from the session nonce and a logical node/window ordinal. Raw node IDs participate in matching but not semantic equality.

## Semantic diff

The differ compares logical refs and produces:

- focus transition;
- active/focused window transition;
- opened and closed windows;
- opened and closed overlays;
- added, removed, and updated generic nodes.

An updated node contains only its descriptor and changed field names. Generic diffs never contain raw editor text.

Overlay roots include dialogs, popup menus, menus, tooltips, alerts, transient non-frame windows, and transient lists inside dialogs or popups. Item extraction prefers ordered `MENU_ITEM` descendants, then `LIST_ITEM` descendants, then bounded visible descendant text.

## Settle barrier

For `summary` and `full`, the coordinator:

1. captures and commits a pre-action baseline;
2. executes the action;
3. captures immediately after dispatch;
4. samples every 25 ms;
5. restarts a 50 ms quiet period after each semantic change;
6. returns the latest candidate when it is quiet or `settleMs` expires.

Intermediate polling states do not receive public revisions. Only the baseline and final state are committed.

Settle statuses are:

- `settled_after_change`;
- `settled_idle`;
- `settle_timeout`.

`settle_timeout` is not an action failure. If a transient semantic change returns to the baseline, the resulting revision is unchanged and the observation includes `transient_change_observed`.

## Failure semantics

If input dispatch succeeds but the post-action accessibility capture fails, the action still returns success:

```json
{
  "result": "success",
  "action": "ide_press_key",
  "observation": {
    "kind": "summary",
    "availability": "unavailable",
    "notices": [
      {
        "code": "observation_unavailable",
        "message": "Accessibility state could not be captured after input dispatch."
      }
    ]
  }
}
```

The coordinator never retries the complete action. Coroutine cancellation is propagated and releases the mutex.

## Privacy and response budgets

- Password values and lengths are not stored or returned.
- A full editor buffer is available only in an explicitly requested full snapshot under the existing snapshot policy.
- Summary history stores only a salted fingerprint and length for editable text.
- Explicit target text is returned in full up to 256 characters.
- Longer target text returns a bounded changed slice plus `beforeLength`, `afterLength`, and `textTruncated=true`.
- Generic descriptor strings are bounded to 128 characters.
- Added, removed, and updated samples are limited to 20 entries per category; total counts remain available.
- Overlay item samples are limited to 10 items with `totalItems` and `truncated`.
- The operational target for a common summary is below 8 KiB and below 300 ms after input dispatch.

## Revision retention

Each session retains at most:

- 32 revisions;
- 16 MiB estimated normalized state;
- 10 minutes of revision history.

The application retains at most eight idle-LRU session histories and 64 MiB globally. Cleanup occurs on observation and diff access. Eviction is always reported explicitly by later baseline/diff lookup.

## Implementation map

- `UiObservationModels.kt`: public serialized request/result types and limits.
- `NormalizedUiState.kt`: privacy-aware normalization and semantic settle comparison.
- `UiRevisionHistory.kt`: session-local revisions, logical refs, matching, and retention.
- `UiStateDiffer.kt`: bounded semantic/window/focus/overlay diffs.
- `UiActionObservationCoordinator.kt`: mutex, baseline/action/capture flow, settle barrier, and failure policy.
- `SnapshotShaper.kt`: opaque revisions and correlation refs in full snapshots/windows.
- `ElementLookup.kt`: refs and opaque revisions in lookup results.
- `UiTestingToolset.kt`: tool arguments, action mapping, diff/capability tools, and correlated read calls.
- `UiAutomationService.kt`: MCP-owned semantic capture, locator/actionability execution, and internal AWT component to raw-node mapping.

No new third-party libraries are required.

## Verification

Deterministic tests cover normalization/privacy, stable refs and revision retention, semantic diffing, settle behavior, coordinator modes/failures/cancellation/serialization, and reflected MCP input/output schemas.

Live verification must use a rebuilt and restarted `idea-dev` plus a fresh MCP session. The acceptance scenario is:

1. capture `ide_snapshot` and retain its opaque revision;
2. right-click an editor and inspect the returned popup/items;
3. press Escape and observe the popup close;
4. open and fill Search Everywhere and inspect target text/results;
5. execute several `none` actions and retrieve one accumulated diff;
6. exercise hover, scroll, and `full` mode;
7. take a screenshot and check its correlated revision;
8. confirm unrelated summaries do not contain a full editor buffer;
9. clean up temporary UI state.

The running IDE must be restarted after rebuild; focused tests alone do not prove that the live MCP server loaded the new classes.
