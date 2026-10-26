# Docs-site kernel

`@ij/docs-site` is the shared kernel of the architecture docs sites. It is a library, not a site. It holds the parts
of a site that depend on no plugin and no product. The package has TypeScript sources and no build step. Node 26 and
Vite 8 run the `.ts` files as they are.

Two sites use the kernel:

- The Air architecture site, [`plugins/air/docs`](../../../plugins/air/docs/README.md), served at `/air/`.
- The dev distribution site, [`build/docs`](../../../build/docs/README.md), served at `/build/`.

The decision records
[The docs site is a shared kernel](../../../plugins/air/docs/decisions/2026-10-14-the-docs-site-is-a-shared-kernel.md)
(Air) and
[The dev distribution docs run on the docs-site kernel](../../../build/docs/decisions/2026-10-14-the-dev-distribution-docs-run-on-the-docs-site-kernel.md)
(build) give the reasons.

## Layout

| Folder | What it holds |
| --- | --- |
| `scripts/` | The node and bun side: the site config type, the model provider type, the diagram grammar, the checks, the doc harvest, the spec harvest, the paths, and the test helpers. |
| `src/` | The browser side: `createSite`, the shell, the route table, the nav and crumb models, the search, the palette, the markdown renderer, the generic pages, the hand-laid diagram, and the shadcn components. |
| `styles/` | The Tailwind entry with the tokens and the type ramp, and `prose.css`, `canvas.css`, and `source.css`. |
| `e2e/` | The static server of a built site, the page helpers, the diagram shots, and the e2e suite of the generic pages. |
| `vite/` | The Vite plugin that runs the harvest of a site before a build and after a change in the dev server. |

The Vite plugin declares the part of the Vite plugin shape that it uses, so the kernel has no Vite dependency.

## Create a site

A site has three parts: a config, a model provider, and the browser parts.

### The site config

`site.config.ts` has a default export of the `SiteConfig` shape of `scripts/siteConfig.ts`. The kernel checks, the
doc harvest, and the browser pages read only this config. The browser loads the file, so it imports nothing at run
time.

| Field | What it gives |
| --- | --- |
| `id`, `title`, `brandMark`, `base` | The site id, the page title, the brand text, and the URL base, for example `/build/`. |
| `siteRoot` | The package directory of the site, relative to the repository root. |
| `contentRoot` | The directory that each doc path is relative to. The build site uses the repository root, `''`. |
| `outDir` | The output directory of `pnpm run build`, relative to the repository root. |
| `docs` | The doc folders: home, notes, guides, explainers, diagrams, glossary, decisions, the extra docs, and the skip names. |
| `specs` | The spec roots, the file suffix, the `plain-1` style or null, and the template sections. |
| `sourceRoots` | The folders that a `source:` reference and a spec target can name. |
| `github` | The GitHub base of a source link and the path of the content root in the repository. |
| `themeStorageKey` | The localStorage key of the theme. The inline script of `index.html` uses the same key. |
| `decisions` | The record folders, the last frozen number, the reference roots, the topics, the hidden topic, and the shared numbers. |
| `guideGroups` | The groups of the Guides page, in page order. |
| `diagram` | The node kinds with their skins, and the values of the `planned:` field. |
| `prose` | The sentence limit, the card limit, the history pattern, and the guide that a finding names. |
| `vocabulary` | The term rules of the vocabulary check, or null. |
| `launchPicker` | The launch picker at `/launch`, or null. |

### The model provider

The doc harvest (`scripts/harvestDocs.ts`) resolves each reference of a diagram node through a `ModelProvider` of the
site. `scripts/modelProvider.ts` declares it:

| Method | What it resolves |
| --- | --- |
| `resolveSymbol(name)` | A type name or an FQN to its declaration. Two declarations give `ambiguous`. |
| `resolveSource(path)` | A `source:` path to its file. |
| `resolveTarget(label)` | Optional. A Bazel label to the `name =` line of its `BUILD.bazel`. Without it, a `target:` fails. |
| `hasModule`, `hasSpec`, `hasConcept` | True when the site has the module, the spec, or the concept. |
| `bundleSources(files)` | Takes the files that the diagrams link, for the source viewer. |
| `resolveSteps(diagram, nodes)` | Optional. The `flowStep:` bindings of the steps. Without it, a `flowStep:` fails. |

Air gives a provider over its Kotlin symbol index and its flows. The build site gives a text search over its source
roots. The kernel knows no language and no build system.

### The browser parts

`src/main.tsx` imports the CSS entry of the site and the kernel styles, then calls `createSite(config, parts)` of
`src/createSite.tsx`. `parts` gives what only the site knows:

- `home`, `nav`, `pages`: the Home page, the nav table, and the renderers of the site pages.
- `routes`: the route extensions, made with `routeBuilder`. They come before the kernel routes, so they can claim a
  kernel URL. Each canonical entry names the `layout` of its page; the next section lists the layouts.
- `data`: the `SiteData` read model. It holds the harvested docs, diagrams, specs, and symbols, and the glob loaders
  of the harvested markdown, sources, and pictures. A site with a launch picker gives the `launch` entries too.
- Optional: `crumbs`, `sectionTrees`, `pageIdentity`, `fenceRenderers`, `refLinks`, `searchProviders`, `stepRef`,
  and `palette`. `src/site.ts` and `src/Shell.tsx` document each one.

A kernel module reads the installed parts through `site()` inside a function, never at module load.

### The page layout

The page is flat on `--background`, under a top bar with a bottom border. The route table gives each page one of four
layouts (`PageLayout` of `src/routeTable.ts`):

| Layout | The columns | Pages of the kernel |
| --- | --- | --- |
| `reading` | The section tree, the document with its crumb row (786px, 70ch of the 16px prose), and the table of contents. | A guide, a decision record, a doc. |
| `catalog` | The reading layout with a 960px document, for cards, tiles and chip rows. | The indexes of the guides, the explainers and the decisions. |
| `tool` | One column across the page, at `text-sm`. | Home, the source viewer, the launch picker. |
| `canvas` | The page fills the outlet and draws its own panes. The page owns its trail. | One explainer. |

A canonical route entry without a `layout` does not compile. `src/pageLayout.tsx` draws the layouts. `sectionTrees`
gives the trees of the site sections by route page, for example the specs of a spec page. The kernel gives the trees of
the guides and the decision records. A reading page without a tree or a table of contents has no empty column for it,
and its columns stay in the middle of the page. Below the `section-nav:` breakpoint the tree opens in a Sheet from the
crumb row.

The tokens are the stock values of the shadcn preset `b0` with the base `base`: Nova, neutral, the stock radius. The
theme color is the stock blue theme. The
domain tokens are a status, the six lanes, and the brand gradient. The kernel also defines the host contract tokens of
the GenUI catalog from the stock values, because a site can render the catalog primitives. A link is the foreground
color with an underline, as in the shadcn docs. The Air record
[The docs sites and the GenUI catalog take the stock shadcn preset](../../../plugins/air/docs/decisions/2026-10-26-the-docs-sites-and-the-genui-catalog-take-the-stock-shadcn-preset.md)
lists every deviation from the stock values and its reason.

The kernel imports each shadcn component by a relative path, for example `./ui/button`. The files under
`src/components/ui/` are stock shadcn. The Air site resolves a kernel import of a GenUI catalog name to the catalog
file (Air ADR 0218). The kernel has no `components.json`: run `pnpm exec shadcn add` in a site, move the new file to
`src/components/ui/`, and change its `@/` imports to relative paths.

## Hand-laid diagrams

A diagram is a YAML file in the `docs.diagrams` folder of the site. The comment at the top of `scripts/diagram.ts`
is the grammar. `validateDiagram` takes the node kinds and the `planned:` values from the `diagram` field of the site
config. A guide, a note, or a spec embeds a diagram with a `diagram` fence. The kernel also accepts the older
`air-diagram` name, which the Air docs use.

An explainer is a markdown doc in the `docs.explainers` folder. Each `## ` heading is a chapter with at most one
diagram. One explainer keeps one lane order in each chapter. A stage can split into sub-lanes of its color. A chapter
answers one question, a step lights two to five nodes, and a card holds at most 150 words. The method is in the Air
[hand-laid diagram guide](../../../plugins/air/docs/guides/hand-laid-diagram-guide.md), and the review list is in
[Air ADR 0204](../../../plugins/air/docs/decisions/0204-an-architecture-explanation-is-laid-out-by-hand.md).

## Checks

Each check has a CLI that takes the config of the site. In `build/docs`:

```sh
node ../../community/tools/docs-site/scripts/diagramFiles.ts site.config.ts
node ../../community/tools/docs-site/scripts/adrFiles.ts site.config.ts
node ../../community/tools/docs-site/scripts/proseLint.ts site.config.ts
node ../../community/tools/docs-site/scripts/specRefs.ts site.config.ts
node ../../community/tools/docs-site/scripts/vocabulary.ts site.config.ts
```

| Check | It fails on |
| --- | --- |
| `diagramFiles` | A grammar or grid error, a fence that names no diagram, and a diagram that no fence shows. |
| `adrFiles` | A record name of another shape, a new number, a duplicate, a bad H1 or `Date:` line, and a dead link or mention. |
| `proseLint` | A long sentence, a history mark, and a long card, in the explainers and the diagram cards. |
| `specRefs` | A dead spec target, `[@test]` link, `.md` link, or anchor, and a break of the `plain-1` rules. |
| `vocabulary` | A term of the site table outside the docs and sections that allow it. |

The doc harvest also fails on a record without a known status or a known topic. The `pnpm run check` script of a
site runs these checks and its own checks. Then it runs the script tests of the site and of the kernel. Air does not run `specRefs` and `vocabulary`. `AirSpecReferencesTest` owns the Air spec
references.

The doc harvest runs the decision, diagram, prose, and vocabulary checks too. It takes them from a check run of
`scripts/checkRun.ts`, which runs each check at most once. The check of the build site gives its run to the harvest,
so no check runs twice there.

### The gate protocol

A JUnit test runs `scripts/gate.ts` of a site on the bun that Bazel declares, `//build:bun`. The gate follows one
protocol:

- The first stdout line is the sentinel, `air-gate 1` or `build-gate 1`. The gate prints it before any check module
  loads.
- Exit 0: the docs are valid. The records follow the sentinel, one per line, with tab-separated fields.
- Exit 3: a check failed. The report follows the sentinel, and no record follows.
- Any other result, or no sentinel: an infrastructure failure, not a check failure.

`air-architecture-model_test` of `plugins/air/tests/architecture-gate` runs the Air gate, and `DevDistDocsSiteTest` runs the build gate. The gate has no
`node_modules`, so each module that it loads imports nothing from npm. `scripts/yamlCompat.ts` falls back to
`Bun.YAML` there.

## Wiring in a site

- **The dependency.** A site depends on the kernel with a `link:` dependency, for example
  `"@ij/docs-site": "link:../../community/tools/docs-site"` in `build/docs`. pnpm does not install the dependencies of
  a `link:` package, so the kernel has its own `package.json` and lockfile. The monorepo has no root `package.json`.
- **One copy of each package.** The site `vite.config.ts` sets `resolve.dedupe` to the kernel `dependencies`, and
  `server.fs.allow` includes the kernel directory.
- **Tailwind.** The site entry imports `@ij/docs-site/styles/tailwind.css`. The kernel entry has an `@source` for
  the kernel `src`. The site adds an `@source` for each folder of its own.
- **TypeScript.** The site `tsconfig.json` extends `tsconfig.base.json` of the kernel.
- **Imports.** A site file in `src/` imports a kernel module by the package name, for example
  `@ij/docs-site/src/theme`. A site file that node or bun runs imports a kernel module by its relative path, because
  the gate has no `node_modules`. A kernel file imports another kernel file by its relative path, never by `@/`.
- **The versions.** Three lockfiles exist: the kernel, `plugins/air/docs`, and `build/docs`. Each site declares the
  shared dependencies at the version of the kernel `package.json`. `DocsSiteToolchainConsistencyTest` fails on a
  drift.

## Toolchain

Each site builds with one node and one pnpm. Bazel declares them:

- `//build:docs_site_node` and `//build:docs_site_pnpm` select the archive of the host. Both aliases are `manual`,
  so `bazel build //...` does not download them.
- `//:MODULE.bazel` pins the versions in `DOCS_SITE_NODE_VERSION` and `DOCS_SITE_PNPM_VERSION`. The archive build
  files are `build/BUILD.docs_site_node.bazel` and `build/BUILD.docs_site_pnpm.bazel`.
- `air-trace serve` asks for the two labels with `bazel cquery --output=files` and builds the Air site with them. It
  installs the kernel first when the kernel has no `node_modules`.

The aliases are in `//build`, not in this directory. The root `.bazelignore` lists `community`, so a package here has
no root label. The pins stay in the ultimate module, per
[build ADR 0009](../../../build/docs/decisions/0009-bun-pins-in-the-ultimate-module.md).

The kernel `package.json` is the source of the versions: `engines.node` and `packageManager`.
`DocsSiteToolchainConsistencyTest` fails when the pins drift from it, or when a site names another `packageManager`.

```sh
./community/tools/bt.cmd --module intellij.projectStructureTests --filter com.intellij.ideaProjectStructure.fast.DocsSiteToolchainConsistencyTest
```

## Run and test

```sh
pnpm install
pnpm run test:scripts
pnpm run typecheck
```

`test:scripts` runs the tests with `scripts/scriptTests.ts`, which loads every test file into one process. The `check`
script of each site also runs the kernel tests. `src/noSiteImports.test.ts` fails on a kernel import from a site. The
kernel has no Playwright run of its own. A site runs the kernel e2e suite from its own suite. It calls
`defineKernelSuite` of `e2e/kernel.test.ts` with its `test` and `expect`. Playwright refuses a second copy of its test
module. After a kernel change, run the checks and the e2e suite of both sites.

## Add a site

1. Create the package folder, for example `<area>/docs`, with `package.json`, `pnpm-workspace.yaml`,
   `tsconfig.json`, `index.html`, and `vite.config.ts`. Copy them from `build/docs`.
2. In `package.json`, add the `link:` dependency on the kernel and the shared dependencies at the kernel versions.
   Set `packageManager` and `engines.node` to the kernel values.
3. Write `site.config.ts`. Pick a new `base`, `outDir`, and `themeStorageKey`, and the doc folders, the topics, and
   the diagram kinds.
4. Write `scripts/modelProvider.ts` and `scripts/harvest.ts`. The harvest calls `harvestDocs` and `harvestSpecs` of
   the kernel and writes `generated/`.
5. Write `src/main.tsx`, `src/site.tsx`, and the Home page, and add the Tailwind entry with its `@source` lines.
6. Add the `check` script with the kernel CLIs of the Checks section, and `test:scripts` with the kernel tests.
7. Write `scripts/gate.ts` with a new sentinel, and a JUnit test that runs it on `//build:bun`.
8. Add the site manifest to the list of `DocsSiteToolchainConsistencyTest`.
9. Add a `.gitignore` with `generated/`. The root `.gitignore` already covers `out/`.
10. Write `README.md` and `AGENTS.md`, and a dated decision record of the site that names the kernel.
