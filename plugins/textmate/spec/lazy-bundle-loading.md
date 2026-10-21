# Lazy loading of TextMate bundles

## Goal

A project open must not parse a TextMate grammar. The project view asks the file type manager about every file,
and `TextMateFileType.isMyFileType` must answer from an index. A grammar is parsed when an editor, an injection, or
another client asks for its language descriptor.

## Facts that shape the design

- The plugin ships 64 built-in bundles, all in the VS Code format. The `package.json` of a bundle lists its languages
  (`extensions`, `filenames`, `filenamePatterns`) and its grammars (`scopeName`, `language`, `path`). The grammar files hold
  the rules and the `include` references. The grammar files are the expensive part: 4.2 CPU seconds for all 64 bundles,
  70 % of it in JSON parsing.
- A grammar includes other grammars by scope name. `text.html.basic` includes `source.css` and `source.js`. The Markdown
  grammar includes 45 other bundles. 38 bundles include no foreign scope. An include resolves when the syntax table compiles.
- A preference applies by scope selector. A VS Code `language-configuration.json` applies to the scopes of its own bundle.
  A classic `.tmbundle` preference can apply to any scope, for example the HTML test bundle applies tag pairs to `meta.tag`,
  which the JSX grammar also produces.
- Clients of `TextMateService`: `TextMateFileType` and its detector (yes or no per file name), the syntax highlighter
  factory and `TextMateEditorUtils` (descriptor by file name), the Markdown fence fallback (descriptor by extension and the
  whole file name mapping), the LLM plugin (descriptor by extension and by file name), the editor handlers (preference and
  shell variable registries), the settings page (`readBundle`, the built-in bundle list).
- Two plugin bundle providers live in the repository. `Trace32TextMateBundleProvider` serves `cmm`, `cmmt`, `men` and `dlg`
  files. `GodotTextMateBundleProvider` serves `gdshader` and `gdshaderinc` files. Such files are rare, yet every start read
  these bundles.

## Decisions

### D1. The index file `lib/bundles/index.txt`

The index is a UTF-8 text file with LF line ends. A line is a key, one space, and a value:

```
bundle <directory name>      starts a bundle
scope <scope name>           starts a grammar of the current bundle
ext <ext>[;<ext>...]         the file extensions of the current grammar, without the leading dot
name <name>[;<name>...]      the file names of the current grammar
pattern <glob>               a matcher of the current grammar
```

A blank line and a line that starts with `#` are ignored. The bundles follow the directory name order. The matchers keep
the `package.json` order. One `ext` line holds every extension of the grammar and one `name` line holds every file name,
separated by `;`, because an extension or a file name never contains `;`. A pattern stays one per line, because a glob can
contain `;`. The decoder uses `indexOf`, `substring` and `split` only, so the startup path loads no JSON library.

The `scope` line stays in the index for three reasons:

1. The public mapping `getFileNameMatcherToScopeNameMapping` maps a matcher to a scope name, and the Markdown fence fallback
   matches an info string against `source.<info>` and `text.<info>` in it.
2. A load must find the bundle that provides an included scope. The scope lines give this map without a read of any
   `package.json`.
3. The syntax table is keyed by scope name, so a request by file name resolves to a scope, then to a bundle.

Without the scope lines, the first load would have to parse every `package.json` to learn the scope map.

The index is mandatory. `loadVSCBundles.main.kts` writes it in a stage after the JSON minification. `TextMateBundlesIndexTest`
in `intellij.textmate.core.tests` compares the file with the bundles and rewrites it when the test runs with
`-Dtextmate.bundles.index.update=true`. A missing file fails the service initialization with the path in the message.

### D2. The API `TextMateService.hasLanguageForFileName(fileName): Boolean`

`isMyFileType` and the file type detector need a yes or no. The old API `getLanguageDescriptorByFileName` returns a compiled
descriptor, so it had to parse a grammar, and the project view paid for all 64 bundles. The new method reads the mapping
only and never loads a bundle. It is a non-abstract method on the abstract service with a default that calls the descriptor
method, so another implementation keeps working. The file type and the detector call the new method.

An alternative is `getScopeNameByFileName`, which returns the scope without a load. It is more general, but no client needs
the scope today, so the boolean stays.

### D3. `ensureInitialized()` loads the index only

Initialization reads the index once and builds, in memory:

- the file name mapping, with `Name` matchers in lower case,
- the map from scope name to bundle,
- empty syntax, preference, snippet and shell variable tables.

No built-in bundle directory is listed or read. The index gives everything the file type manager and the settings page need.

A user bundle or a plugin bundle (`TextMateBundleProvider`) has no index. Initialization reads its grammar list, which for
a VS Code bundle is the `package.json` only, and for a classic bundle the grammar plists, because the file types are inside
them. Initialization also reads and registers the preferences of such a bundle, because a classic preference can apply to a
scope of another bundle. This pass runs in parallel as before, and a standard installation has no such bundle. A plugin bundle
whose provider declares its extensions is read at the first request instead, see D7.

### D4. `getLanguageDescriptorByFileName` and `getLanguageDescriptorByExtension` load on demand

The request maps the file name to a scope and the scope to a bundle. A loaded bundle answers from the syntax table at once.
An unloaded bundle loads in a cancellable job on the IO dispatcher under the registration lock: the job parses the grammars,
registers the preferences of a built-in bundle, registers the snippets in tests, and rebuilds the four tables. A concurrent
request awaits the same job. A request that finds a new registration after the job, because `reloadEnabledBundles` ran in
between, loads the bundle of the new registration.

Include policy: the job also loads every registered bundle that provides a scope the loaded grammars include, and repeats
until no provider is left. This keeps the old highlighting of embedded code, for example CSS and JavaScript inside HTML.
The cost is bounded by the include graph of the requested grammar: 38 bundles load alone, HTML loads 3, PHP 8, Ruby 12,
and the Markdown, LaTeX, AsciiDoc and MDX grammars load 47 to 50. The last group costs what every start cost before, and
only when a file of that type opens through TextMate.

A failed load is reported once: an error log entry for a built-in bundle, a notification with a disable action for a user
bundle. The bundle counts as loaded afterwards.

### D5. Registries

The preference, snippet and shell variable registries and the custom color map are snapshots. A getter returns the current
snapshot and never loads a bundle. A load rebuilds the snapshots. The preferences of a built-in bundle register when the
bundle loads, because a VS Code preference applies to the scopes of its own bundle.

### D6. Reload and settings

`reloadEnabledBundles` registers the enabled bundles again from the index and the settings, drops the loaded state, and
fires the file type change event when the mapping changed. `discoverBuiltinBundles` maps the index to bundle paths and
keeps the list in `TextMateBuiltinBundlesSettings.builtinBundles` for the settings page.

### D7. A plugin bundle provider declares its extensions

`TextMateBundleProvider.getExtensions(): Set<String>` returns the extensions of the files that the bundles of the provider
support, without the leading dot. The extensions are case-insensitive. The default is an empty set, which keeps D3 for an
existing provider. A file name or a glob cannot be declared, because the two providers in the repository need extensions only.

A provider with a non-empty set is lazy. Initialization keeps a map from the lower-case extension to the bundle and reads
nothing from the bundle directory. `hasLanguageForFileName` answers from this map as it answers from the mapping. A descriptor
request for such an extension loads the bundle in a job as in D4: the job reads the grammars, the preferences and, in tests,
the snippets, and it loads the bundles of the included scopes. The scope of a declared extension is the scope of the grammar
that lists the extension. When no grammar of the bundle lists it, the scope of the first grammar fills the gap, unless another
bundle maps the extension. The rebuilt mapping holds the declared extensions with their scopes, so
`getFileNameMatcherToScopeNameMapping` and the Markdown fence fallback see them after the load. A declared extension that a
grammar lists wins over the same extension of another bundle, as a plugin bundle did before.

`Trace32TextMateBundleProvider` and `GodotTextMateBundleProvider` declare their extensions.

## Non-goals

- VS Code `injectTo` grammars. The plugin never supported them.
- Incremental compilation of the syntax table. A load recompiles all loaded grammars from their retained raw nodes.
- A cache of parsed grammars. This is a separate change.

## Tests

- `TextMateBundlesIndexTest` (core tests): the index equals the bundles; encode and decode keep an index.
- `TextMateLazyBundleLoadingTest` (plugin tests): the mapping answers without a load; a descriptor request loads one bundle;
  HTML loads CSS and JavaScript with it; the preferences of a loaded bundle are available; a provider with declared extensions
  answers without a load and loads on the first request, with the extension in any case.
- Existing: the editor acceptance tests (the JSX typed handler test needs the classic cross-bundle preferences of D3),
  the Markdown fence tests (one test fails on master before this change).

## Code shape

- `intellij.textmate.core`: `bundles/TextMateBundlesIndex.kt` holds the model, `decode`, `encode`, `bundleIndexEntry` and
  `TextMateGrammar.scopeName()`. `srcJvmMain/bundles/TextMateBundleDirectories.kt` holds `readBundleDirectory` and
  `buildBundlesIndex`. `TextMateSyntaxTableBuilder` records the `include` targets and exposes `unresolvedIncludedScopeNames()`.
- `intellij.textmate`: `TextMateServiceImpl` keeps a `BundleRegistry` per registration (bundles, mapping, scope to bundle,
  declared extension to bundle, the four builders, the custom colors) and a `RegisteredBundle` per bundle (path, the load job,
  the loaded flag) of three kinds: `Builtin` with the index entry, `External` with the reader, the grammars and the preferences
  of a user or plugin bundle, `Declared` with the extensions of a lazy provider and the scopes the load found for them.
  `TextMateService` gains `hasLanguageForFileName`. `TextMateFileType` calls it. `TextMateBundleProvider` gains
  `getExtensions`. `loadVSCBundles.main.kts` gains the `writeBundlesIndex` stage.
