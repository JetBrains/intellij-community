# Failures, ambiguity, and conflicts

Read this page when a `rename_refactoring` response carries `error`, `candidates` or `conflicts`.
Every response listed here changed no file, except `write_failed`. That one says what it wrote.

## The name did not resolve to one symbol

| `error.kind` | Meaning | Action |
|---|---|---|
| `ambiguous_symbol` | Several symbols carry this name here. The response lists `candidates` | Repeat the call with every argument it had and add `--targetIndex`, or narrow it with a `--contextSnippet` that shows the one you mean |
| `symbol_not_found` | No symbol with this name is declared in this file | Pass the file that declares the symbol. Find it with `search_symbol` |
| `name_mismatch` | The position or the snippet resolves to another name. The hint names it | Correct the position, or correct `symbolName` |
| `snippet_not_found` | `contextSnippet` is not in the file | Read the file again and copy the snippet from its current text |
| `snippet_not_unique` | `contextSnippet` matches the file more than once | Extend the snippet until it is unique |
| `position_out_of_bounds` | The line or the column is outside the file, or the column is past the end of the line | Both numbers are 1-based. Copy them from `search_symbol` output |
| `target_index_out_of_range` | No candidate carries this `targetIndex` | Use an index from the `candidates` list of the previous call |

`candidates` entries carry `targetIndex`, `kind` such as `class` or `parameter`, and a `symbol` block
with the declaration line and a snippet of the declaration. Choose from the `kind` and the snippet.

`targetIndex` picks from the list of one call. The tool rebuilds that list from the other arguments,
so repeat `pathInProject`, `symbolName` and `contextSnippet` exactly as the call that listed the
candidates had them. A changed argument builds another list, and the index then means another symbol.

Ask the user instead of choosing when two candidates have the same `kind`, and the snippets do not
tell you which one the request meant.

## The new name was refused

| `error.kind` | Meaning | Action |
|---|---|---|
| `new_name_invalid` | The name is not a valid identifier for the language | Correct the name. A JVM identifier holds no space and no punctuation |
| `new_name_matches_current` | The symbol already carries this name | No change is needed. Tell the user |
| `new_name_refused` | The engine refused the name for the symbol. The hint carries its message | Read the hint. It usually names a clash |
| `not_renamable` | The symbol is compiled, or it is a library symbol outside the project | Report to the user. A library symbol changes upstream |

## A conflict refused the rename

`conflicts` is non-empty and `error.kind` is `conflicts_found`. Each entry carries a `description`
from the engine and a `filePath`.

A conflict means the new name would break the code, most often because it collides with a member that
already exists in the same scope. Choose another `newName`. Check the next name with
`--preview true`, which reports the conflicts and writes nothing.

Ask the user instead of choosing when there are more than five conflicts. A long list means the name
is wrong for this symbol, not that one entry needs a fix.

## The rename could not run

| `error.kind` | Meaning | Action |
|---|---|---|
| `read_only_usages` | A usage or a declaration sits in a file that cannot be written. The hint lists the files | Report to the user. The rename applies nothing until those files are writable. A file that only the VCS holds needs a checkout, which the tool does not do |
| `incomplete_use_scope` | The project holds an unloaded module that uses the symbol | Report to the user. The module must be loaded first, or the rename leaves a stale reference |
| `index_not_ready` | The indexes were not ready, so the usage set would be incomplete | Wait, then repeat the call once |
| `unsupported_reference_language` | A reference belongs to a language that cannot take part in the rename | Report to the user |
| `plan_stale` | The code changed while the rename was starting | Read the file again, then repeat the call once |
| `write_failed` | The rename started to write, and a step refused. The hint says what it wrote | Read the files again. Do not repeat the call until you check the code |
| `unknown` | No specific reason was reported | Repeat the call once. Report to the user when it repeats |
| `not_applied` | The language has no headless rename support, and the legacy path wrote nothing. It reports no reason | Report to the user. Do not repeat the call |
| `preview_not_supported` | The language has no headless rename support, so `--preview` reports nothing. Nothing was analyzed | Call again without `--preview`, or read the code yourself first |

Repeat a call at most once, and only for `index_not_ready`, `plan_stale` and `unknown`. A second
failure is a real problem, so tell the user instead of retrying again.

## `partialResultReason`

The response can carry `partialResultReason` while the project is indexing. The rename still applied
when `applied` is true, but the usage set could be incomplete. Report this limitation to the user.
Do not repeat the original call: the symbol can already have its new name.

## What the tool does not do

- **A string, a comment and a plain-text occurrence keep the old name.** The tool rewrites code
  references only. Edit these occurrences separately only when the user requests it.
  Do not search for them to verify a successful rename.
- **Automatic renamers follow the IDE settings.** `applyAutomaticRenamers` defaults to `true`.
  A related declaration can keep its name when its automatic renamer is disabled or unavailable.
- **Select one primary symbol per call.** Automatic renamers can rename related declarations in the same call.
  Use separate calls for other symbols that the user requests.
