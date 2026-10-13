# Docs-site kernel

`@ij/docs-site` is the shared kernel of the architecture docs sites. It holds the parts of a site that do not
depend on one plugin or one product. The package has TypeScript sources and no build step. Node 26 and Vite 8 run
the `.ts` files as they are.

## Consumers

- The Air architecture site, `plugins/air/docs`.
- The dev distribution site, `build/docs`. This site does not exist yet.

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
  doc harvest (`harvestDocs.ts`), the doc pictures, the source paths, the repository paths (`paths.ts`), and the test
  helpers.
- `src/`: the browser side. The app shell (`Shell.tsx`, `createSite.tsx`), the route table and the router, the nav
  and crumb models, the search and the command palette, the markdown renderer, the generic pages (guides,
  explainers, decisions, docs, the source viewer), the hand-laid diagram, the shadcn components, and the theme state.
- `styles/`: the Tailwind entry and the site styles. A site imports them.
- `e2e/`: the static server of a built site, the page helpers, the diagram shots, and the e2e suite of the generic
  pages (`kernel.test.ts`).

## Create a site

A site calls `createSite(config, parts)` in its `src/main.tsx`, after it imports its CSS entry and the kernel styles.
`parts` gives what only the site knows:

- `home`, `nav`, `pages`: the Home page, the nav table, and the renderers of the site pages by route page.
- `routes`: the route extensions of the site pages, made with `routeBuilder`. They come before the kernel routes.
- `data`: the `SiteData` read model: the harvested docs, diagrams, specs, and symbols, and the glob loaders of the
  harvested markdown, sources, and pictures (`harvestedFiles`). The site calls `import.meta.glob` itself.
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
the paths of the site, the decision folders, the decision topics, the guide groups, the diagram node kinds, and the
prose limits. The kernel checks and the doc harvest read only this config, and a site gives its model to the harvest
as a `ModelProvider`.

Each check has a CLI that takes the config of the site:

```sh
node ../../../community/tools/docs-site/scripts/diagramFiles.ts site.config.ts
node ../../../community/tools/docs-site/scripts/adrFiles.ts site.config.ts
node ../../../community/tools/docs-site/scripts/proseLint.ts site.config.ts
```

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

## Tests

```sh
pnpm install
pnpm run test:scripts
pnpm run typecheck
```

The `check` script of the Air site also runs `test:scripts` of the kernel. `src/noSiteImports.test.ts` fails on a
kernel import from a site. A site runs the e2e suite of the kernel from its own Playwright suite: it calls
`defineKernelSuite` with its `test` and `expect`, because Playwright refuses a second copy of its test module.
