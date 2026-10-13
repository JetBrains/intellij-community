# Docs-site kernel

`@ij/docs-site` is the shared kernel of the architecture docs sites. It holds the parts of a site that do not
depend on one plugin or one product. The package has TypeScript sources and no build step. Node 26 and Vite 8 run
the `.ts` files as they are.

## Consumers

- The Air architecture site, `plugins/air/docs`.
- The dev distribution site, `build/docs`.

A site depends on the kernel with a `link:` dependency in its `package.json`:

```json
"@ij/docs-site": "link:../../../community/tools/docs-site"
```

pnpm does not install the dependencies of a `link:` package. So run `pnpm install` in the kernel too, before the
first build of a site.

## Contents

- `scripts/`: the node and bun side. The site config (`siteConfig.ts`), the model provider (`modelProvider.ts`), the
  hand-laid diagram grammar (`diagram.ts`), the fence helpers (`fence.ts`), the explainer chapters, the decision
  record metadata, the decision check (`adrFiles.ts`), the diagram check (`diagramFiles.ts`), the prose check, the
  vocabulary check (`vocabulary.ts`), the spec check (`specRefs.ts`), the doc harvest (`harvestDocs.ts`), the spec harvest (`harvestSpecs.ts`), the doc pictures, the source paths, the
  repository paths (`paths.ts`), and the test helpers.
- `src/`: the browser side. The app shell (`Shell.tsx`, `createSite.tsx`), the route table and the router, the nav
  and crumb models, the search and the command palette, the markdown renderer, the generic pages (guides,
  explainers, decisions, docs, the source viewer, the launch picker), the hand-laid diagram, the shadcn components, and the theme state.
- `styles/`: the Tailwind entry and the site styles. A site imports them.
- `vite/`: the Vite plugin that runs the harvest of a site before a build and after a change in the dev server
  (`harvestPlugin.ts`). It declares the part of the Vite plugin shape that it uses, so the kernel has no Vite
  dependency.
- `e2e/`: the static server of a built site, the page helpers, the diagram shots, and the e2e suite of the generic
  pages (`kernel.test.ts`).

## Create a site

A site calls `createSite(config, parts)` in its `src/main.tsx`, after it imports its CSS entry and the kernel styles.
`parts` gives what only the site knows:

- `home`, `nav`, `pages`: the Home page, the nav table, and the renderers of the site pages by route page.
- `routes`: the route extensions of the site pages, made with `routeBuilder`. They come before the kernel routes.
- `data`: the `SiteData` read model: the harvested docs, diagrams, specs, and symbols, and the glob loaders of the
  harvested markdown, sources, and pictures (`harvestedFiles`). The site calls `import.meta.glob` itself. A site with
  a `launchPicker` in its config gives the `launch` entries too. With no entry, the site has no launch route.
- Optional: `crumbs`, `canvasPages`, `pageIdentity`, `fenceRenderers`, `refLinks`, `searchProviders`, `stepRef`, and
  `palette`. `site.ts` and `createSite.tsx` document each one.

A kernel module reads the installed parts through `site()` inside a function, never at module load.

## The ui primitives

The kernel imports each shadcn component by a relative path, for example `./ui/button`. The files under
`src/components/ui/` are the defaults. The Air site renders the GenUI catalog copy of the primitives that the
catalog has (Air ADR 0218): a Vite plugin in `plugins/air/docs/vite.config.ts` resolves a kernel import of such a
name to the catalog file. The kernel copies of those names are stock shadcn without the Air deviations.

## Site config

Each site has a `site.config.ts` with a default export of the `SiteConfig` shape (`scripts/siteConfig.ts`). It gives
the paths of the site, the decision folders, the decision topics, the guide groups, the diagram node kinds, the
prose limits, the term rules of the vocabulary check, and the launch picker. The kernel checks and the doc harvest read only this config, and a site gives its model to the harvest
as a `ModelProvider`.

Each check has a CLI that takes the config of the site:

```sh
node ../../../community/tools/docs-site/scripts/diagramFiles.ts site.config.ts
node ../../../community/tools/docs-site/scripts/adrFiles.ts site.config.ts
node ../../../community/tools/docs-site/scripts/proseLint.ts site.config.ts
node ../../../community/tools/docs-site/scripts/vocabulary.ts site.config.ts
node ../../../community/tools/docs-site/scripts/specRefs.ts site.config.ts
```

The spec check (`specRefs.ts`) resolves the `targets` and the `[@test]` links of the specs, and the relative `.md` links
and anchors of the specs and the prose docs. With `specs.style: 'plain-1'`, it also checks the sentence budget, the
requirement budget, the template sections, and the style of each spec under the first spec root. The Air site does not
run it yet. Its guides link the hash routes of the site, and `AirSpecReferencesTest` owns the Air spec references.

## Import convention

- A site file that the browser loads (`src/`) imports a kernel module by the package name, for example
  `@ij/docs-site/src/theme` or `@ij/docs-site/scripts/docMeta`.
- A site file that node or bun runs (`scripts/`, `e2e/`, a config file) imports a kernel module by its relative
  path, for example `../../../../community/tools/docs-site/scripts/diagram.ts`. The Air gate runs on Bazel bun
  without `node_modules`, so a package name does not resolve there.
- A kernel file imports another kernel file by its relative path. It never uses the `@/` alias of a site.

## Wiring in a site

- `vite.config.ts` sets `resolve.dedupe` to the `dependencies` of the kernel `package.json`, so the bundle has one
  copy of each. `server.fs.allow` includes the kernel directory.
- The site Tailwind entry imports `@ij/docs-site/styles/tailwind.css`. The kernel entry has an `@source` for the
  kernel `src`, so Tailwind finds the classes of the kernel components.
- The site `tsconfig.json` extends `tsconfig.base.json` of the kernel.

## Toolchain

Every site builds with one node and one pnpm. Bazel declares them:

- `//build:docs_site_node` and `//build:docs_site_pnpm` select the archive of the host. Both aliases are `manual`,
  so `bazel build //...` does not download them.
- `air-trace serve` is the first client. It asks for the two labels with `bazel cquery --output=files` and builds the
  Air site with them. It installs the kernel first when the kernel has no `node_modules`.
- `//:MODULE.bazel` pins the versions in `DOCS_SITE_NODE_VERSION` and `DOCS_SITE_PNPM_VERSION`, with the checksums
  in `DOCS_SITE_NODE_PLATFORMS` and `DOCS_SITE_PNPM_PLATFORMS`. The archive build files are
  `build/BUILD.docs_site_node.bazel` and `build/BUILD.docs_site_pnpm.bazel`.

The aliases are in `//build`, not in this directory. The root `.bazelignore` lists `community`, so a package here
has no root label. The pins stay in the ultimate module, per
[build ADR 0009](../../../build/docs/decisions/0009-bun-pins-in-the-ultimate-module.md), and the community module cannot
see the archives of the root module.

The kernel `package.json` is the source of the versions: `engines.node` and `packageManager`.
`DocsSiteToolchainConsistencyTest` fails when the pins drift from it. It also fails when a site names another
`packageManager`, or declares a kernel dependency at another version.

## Tests

```sh
pnpm install
pnpm run test:scripts
pnpm run typecheck
```

The `check` script of the Air site also runs `test:scripts` of the kernel. `src/noSiteImports.test.ts` fails on a
kernel import from a site. A site runs the e2e suite of the kernel from its own Playwright suite: it calls
`defineKernelSuite` with its `test` and `expect`, because Playwright refuses a second copy of its test module.
