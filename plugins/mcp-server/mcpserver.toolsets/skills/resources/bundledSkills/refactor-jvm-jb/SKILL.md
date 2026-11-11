---
name: refactor-jvm-jb
description: Apply IDE refactorings to Java and Kotlin code. Supported operations include rename. Use whenever a request needs a supported operation, even without naming this skill or as part of a larger task. Rename requests include "rename X to Y", "change the class name", and renaming a method, field, or variable with its usages. Exclude plain text replacement and operations not listed here.
allowed-tools: execute_tool
metadata:
  author: JetBrains
---

# JVM Refactoring

The IDE resolves the symbol through the PSI and the reference index, then rewrites every reference in
one call. A text replacement looks done and silently misses an override, a method reference,
reflection, a Spring bean name, and a `*.properties` key. Always prefer the tool.

## Trust the result

`applied: true` with no `error`, `conflicts`, or `partialResultReason` means the rename is complete.
Report `affects` and answer the user. Do not verify: do not re-read files, inspect a diff, search for
the old name, run diagnostics, build, or run tests. Check further only when the user asks for it.
For any other response, follow [reference/failures.md](reference/failures.md).

## Invoke a tool

Call through `execute_tool`. The first token is the tool name, then `--flag value` pairs. Quote a
value that holds a space with single quotes. The command is one line, so keep every value on one line.

```
execute_tool(command="rename_refactoring --pathInProject src/main/java/app/OrderService.java --symbolName OrderService --newName OrderProcessor --applyAutomaticRenamers true")
```

| Refactor | Tool | Required flags |
|---|---|---|
| Rename a symbol and every reference | `rename_refactoring` | `--pathInProject --symbolName --newName` |

Run the rename itself first. A conflict and a wrong target both stop the call and change no file, so
a preview pass up front only spends a call.

Add `--preview true` when a conflict already stopped a call, or when the user asks first what the
rename touches. The preview reports `affects` and `conflicts` and writes nothing, so it tests another
`newName` before a write. Repeat the call without `--preview` to apply the rename.

The preview covers Java, Kotlin and SQL. Another language answers `preview_not_supported`, because
it has no engine that reports the affected files without a write. Run the rename itself there.

## Related names

`--applyAutomaticRenamers` defaults to `true`, so the IDE also renames related declarations, such as
variables named after a class, as its settings allow. Pass `false` when the user wants only the selected
symbol renamed, and keep the same value when applying a preview. Do not check automatic renames separately.

`--searchInComments` and `--searchInText` default to `false`, so comments, string literals and plain-text
occurrences keep the old name. Pass `true` only when the user asks to rename them too.

## Point at the symbol

`--symbolName` alone is enough only when the file declares one symbol with that name. Otherwise add
one of these, in this order.

| Flag | Use it when | Where the value comes from |
|---|---|---|
| `--contextSnippet` | Preferred. The name is not unique in the file | One line of the file text you just read. It must contain `symbolName` and match the file once |
| `--line` with `--column` | You already ran `search_symbol` for this symbol | Copy `startLine` and `startColumn` from the output. Both are 1-based |
| `--targetIndex` | An earlier call answered with `candidates` | The `targetIndex` of the entry you want |

**Copy a position. Never count lines.** A number you counted points at the wrong symbol. A snippet you
copied fails loudly instead. `--symbolName` also guards a position, so a position that resolves to
another name refuses the rename and changes nothing.

### Worked example: a name that a parameter shadows

```
search_text(q="private int counter")
execute_tool(command="rename_refactoring --pathInProject src/main/java/app/Stats.java --symbolName counter --newName hitCount --contextSnippet 'private int counter;'")
```

The snippet selects the field. The parameter named `counter` in the same file keeps its name.

## Rules

1. **Use the tool.** An ambiguous name, a missing parameter and a conflict are input mistakes you can
   correct. Fall back to a text edit only when `execute_tool` is absent, or when a real call failed
   and no argument can fix it.
2. **Trust the answer. Do not build to check it.** `applied: true` confirms the rename, and `affects`
   reports its scope. Do not add a "verify", "sanity check", or "just to be sure" step: no diff, no
   search for the old name, no re-read, no build or tests. The IDE already applied the rename.
   `applied: false` changed no file unless `error.kind` is `write_failed`.
3. **Never repeat the same arguments.** Change one argument between calls. An ambiguous name needs
   `--targetIndex` or `--contextSnippet`, not a retry.
4. **A library or compiled symbol cannot be renamed.** Report it to the user, because the change belongs upstream.

Read [reference/failures.md](reference/failures.md) when a response carries `error` or `conflicts`. It
lists every `error.kind`, and what to do about it. Read
[reference/tools.md](reference/tools.md) only for a parameter this page does not explain.
