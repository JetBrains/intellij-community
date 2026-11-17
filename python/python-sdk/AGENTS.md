# Python interpreters: the interpreter library flow

Read this before you plan, edit or review code that reads, sets, stores or tests a Python interpreter. The rules
apply also outside `python-sdk/`, for example in `python-pyproject/`, run configurations and the interpreter UI.

## The two flows

The registry key `python.interpreter.library.entries` selects the flow, see `isInterpreterLibraryFlowEnabled()` in
`backend/src/com/intellij/python/sdk/backend/PyInterpreterLibraryFlow.kt`. The key needs a restart, so one session
keeps one flow.

- **The interpreter library flow** is the default (`true`). It does not use the application SDK table. All new code
  targets this flow.
- **The legacy flow** (`false`) keeps the interpreter as the Python SDK of a module in the SDK table. It is deprecated.
  Keep it working, but add no feature to it.

## How the interpreter library flow works

1. **A module names its interpreter by a library entry.** The `.iml` of the module holds one project library
   dependency, for example `<orderEntry type="library" name="Python: . native:uv:.venv" level="project"/>`.
   The name is `Python: <owner dir> <ref>`, see `PyInterpreterLibraryName`.
   - The owner dir is the base directory of the Python project that owns the interpreter, see
     `PyInterpreterOwnerDir`. It is relative to the project, with `/` separators. `.` is the project directory.
   - The ref is a `PyInterpreterRef` (`common/`): `native:<tool>:<env ref>` or `target:<target id>:<tool>:<env ref>`.
     Only the tool reads the env ref.
   - A uv workspace member names the owner dir of the workspace root. The members so share one library.
2. **The library is not saved.** `PyInterpreterLibrarySync` (`python-pyproject`) builds the `LibraryEntity`, its
   roots, and the `sys.path` source roots of each module. These entities have `PyInterpreterLibraryEntitySource`, so
   only the local workspace model cache holds them. The SDK of a module stays a Java SDK.
3. **A record holds the facts.** `PythonInterpreterRecords` saves one record for each `PythonInterpreterKey` (owner
   dir and ref) in `workspace.xml`. The record holds the UUID, the version, `sys.path`, the requirements path, the
   added and excluded paths, and the path mappings of a target. An entry without a record is valid, for example after
   a fresh checkout. `PythonInterpreterRegistry.adoptEntryKey` then makes the record.
4. **An SDK lives only in memory.** `PythonInterpreterRegistry` builds an `Sdk` from the ref and the record, and
   `PythonInterpreterSdks` holds it for the project. No SDK table holds it. The project disposes it when it closes.
5. **Facts and roots are separate.** `PythonInterpreterDetails` is the only writer of facts. `PythonInterpreterRoots`
   derives the class roots and the source roots from the facts and from each `PythonInterpreterRootsContributor`.
   An update has two levels:
   - `PythonEnvironments` updates one environment (`PythonEnvironmentKey`) once for all the projects that use it,
     for example the probe of the version and the skeleton generation. Its steps are `PythonEnvironmentStep`
     (`Pythonid.environmentUpdateStep`).
   - `PythonInterpreterUpdater` updates one interpreter in one project. Its steps are `PythonInterpreterUpdateStep`
     (`Pythonid.interpreterUpdateStep`).
6. **Old projects migrate one way.** `migrateSdkReferences` (`python-pyproject`) replaces the Python SDK of each module
   and the SDK name of each Python facet with an entry. Then it removes the SDK reference. It runs on each model pass,
   after the JPS synchronization. Run configurations store the library name in the `INTERPRETER` field, see
   `interpreterTextOf`. The old `SDK_NAME` and `SDK_HOME` fields are a fallback for reading only.

## Rules

- **Read** an interpreter with `PyProject.getInterpreter()`. It reads the `EvoPyProjectModel` snapshot.
- **Set** an interpreter only with `PyProject.setPythonInterpreter(...)`. It is the only write path and the only way
  to add an interpreter to the registry.
- **Do not read the SDK table.** `ProjectJdkTable`, `PythonSdkUtil.findPythonSdk` and the SDK of a module find nothing
  in this flow. Only `PythonInterpreterRegistry` and the migration read the table.
- **Do not put facts in an `Sdk`.** No user data, and no `versionString` as the source of truth. Write facts through
  `PythonInterpreterDetails`. Add new update work as a step, not in the updater. Use a
  `PythonEnvironmentStep` when the result is the same for each project, else a `PythonInterpreterUpdateStep`.
- **An interpreter belongs to one PyProject.** Do not add a project SDK, an inherited SDK, a global list of
  interpreters, or a lookup without a PyProject. Two PyProjects can have the same ref for two interpreters.
- **Do not save local paths in a shared file.** The `.iml` holds only the library name. Local paths go in the record,
  in `workspace.xml`.
- **Walk order entries with the helpers.** Use `isPyInterpreterLibrary()`, `isIgnoredInterpreterEntry()` and
  `isInterpreterLibraryCarrier()` from `PyInterpreterLibraryEntries.kt`. Do not treat `JdkOrderEntry` as the
  interpreter.
- **All Interpreters is a history.** Do not let it remove an interpreter that a project uses, see
  `EvoPyProjectModel.Snapshot.isInUse` in `PyAllInterpretersConfigurable.kt`.
- **Use `PythonInterpreter`, not `Sdk`,** outside `intellij.python.sdk.backend`, see
  [the Python index](../.ai/index.md#never-use-sdk-use-pythoninterpreter).

## Tests

- Set the flow explicitly in a test, so the test does not depend on the default. In JUnit 5 use
  `@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")`. In an old JUnit test use
  `RegistryKeyRule`.
- For a mock SDK or an SDK of a real environment, use `Module.setPythonSdkForTests(sdk)`, and remove it with
  `Module.removeTestSdk(sdk)`.
- For a real environment, call `awaitPythonInterpreterReady(interpreter, project)` before you check roots or facts.
- When a test fails because a module without a PyProject "had" an SDK, fix the fixture. Do not add a production
  fallback to a stored or project SDK.
- Examples: `PythonInterpreterModuleRootsTest`, `PyInterpreterEntryWithoutRecordTest`, `PySdkReferenceMigrationTest`,
  `PyInterpreterRecordPortabilityTest`, `PyInterpreterLibraryNameTest`.
