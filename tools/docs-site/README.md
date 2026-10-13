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

- `scripts/`: the node and bun side. The hand-laid diagram grammar (`diagram.ts`), the fence helpers (`fence.ts`),
  the explainer chapters, the decision record metadata, the prose check, the doc pictures, the source paths, and
  the test helpers.
- `src/`: the browser side. The diagram layout, the diagram card, the shared node chrome, the diagram edges, and
  the theme state.
- `styles/`: the Tailwind entry and the site styles. A site imports them.

## Import convention

- A site file that the browser loads (`src/`) imports a kernel module by the package name, for example
  `@ij/docs-site/src/theme` or `@ij/docs-site/scripts/docMeta`.
- A site file that node or bun runs (`scripts/`, `e2e/`, a config file) imports a kernel module by its relative
  path, for example `../../../../community/tools/docs-site/scripts/diagram.ts`. The Air gate runs on Bazel bun
  without `node_modules`, so a package name does not resolve there.
- A kernel file imports another kernel file by its relative path. It never uses the `@/` alias of a site.

## Wiring in a site

- `vite.config.ts` sets `resolve.dedupe` for `react`, `react-dom`, and `@xyflow/react`, so the bundle has one copy
  of each. `server.fs.allow` includes the kernel directory.
- The site Tailwind entry imports `@ij/docs-site/styles/tailwind.css`. The kernel entry has an `@source` for the
  kernel `src`, so Tailwind finds the classes of the kernel components.
- The site `tsconfig.json` extends `tsconfig.base.json` of the kernel.

## Tests

```sh
pnpm install
pnpm run test:scripts
pnpm run typecheck
```

The `check` script of the Air site also runs `test:scripts` of the kernel.
