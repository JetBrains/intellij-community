---
name: jvm-imports
description: "Use when Java or Kotlin code in a project open in IntelliJ IDEA needs its imports. Trigger right after you write, paste, or move such code. Trigger on a name the file does not import yet: a class, a static method, a static field, an extension function, a top-level function, a typealias, or an enum entry. Trigger also when a build or an inspection reports an unresolved name in a .java or a .kt file. Do not use it for another language, for an unused import, or for a rename."
allowed-tools: execute_tool
metadata:
  author: JetBrains
---

# JVM imports

Write the code and leave the imports out. One call then adds them with the code insight of the IDE.

The IDE resolves a short name the way the editor does, so it finds an extension function, a top-level
function, a typealias, and a static member, and it knows which of two classes with the same name the
file can see. Guessing a fully qualified name instead looks done and fails at the build.

## Invoke the tool

Call through `execute_tool`. The first token is the tool name, then `--flag value` pairs. A list
takes a single-quoted JSON array.

```
execute_tool(command="add_missing_imports --files '[\"src/main/java/com/example/Order.java\"]'")
```

| Flag | Meaning |
|---|---|
| `--files` | JSON array of project-relative `.java` and `.kt` paths. Send every file you just edited in one call. |
| `--mode` | `apply` (default) writes the files and saves them. `diff` only previews the change and writes nothing. |
| `--ambiguity` | `report` (default) reports a name with several candidates. `best` takes the first candidate of the IDE. |
| `--optimize` | `false` (default) gives every name its own import. `true` then runs Optimize Imports with the style of the project: it can join imports into an import on demand, and it removes the unused imports. |
| `--timeout` | Milliseconds. Raise it for a large batch. |

## Read the answer

Each file entry holds a diff and two lists.

- `diff` — every import the file gained, a Java `import static` included. In the `apply` mode the file
  on the disk already holds it, so read the file again before you edit it. In the `diff` mode nothing
  was written. A name the file already resolves is in no list, and that is a success.
- `ambiguous` — a name with several candidates, and no import for it. `lineText` shows how the code
  uses the name. Pick the candidate that fits the task, then add that one import yourself. Do not re-run with `--ambiguity best` unless the
  candidates only differ in a package you do not care about.
- `unresolved` — a name the tool did not import:

| `reason` | What to do |
|---|---|
| `symbol_not_found` | The module of the file sees no declaration of the name. You most likely invented it: find the real name with `search_symbol` before you write more code. If `search_symbol` finds it in another module, add the module dependency; do not pick a different class to make the error go away. |
| `fix_not_applied` | The IDE refused that one change and the name still does not resolve. Add that import by hand. |

A line number is 1-based. In the `apply` mode it points at the file after the write. In the `diff`
mode it points at the file as it is on the disk.

A file entry with a `notAnalyzedReason` was skipped, for example because it is not Java or Kotlin.
`timedOut: true` means the remaining files are missing from `items`; call again for them.

## Rules

1. **Use the tool; do not write an import by hand** while the tool can do it. A hand-written import
   picks the wrong package as soon as two classes share a short name.
2. **Trust the answer. Do not build to check it.** `diff` is the confirmation. A build
   never changes an import that is already in the file, it only costs time.
3. **One call per batch of files**, not one call per file.
4. **No `diff` with an empty `ambiguous` and an empty `unresolved` means the file needs no
   import.** That is a success, not a failure.
5. **Do not call the tool to remove an unused import.** It only adds, unless you pass `--optimize`,
   which also removes every unused import of the file.
