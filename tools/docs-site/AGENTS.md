# Docs-site kernel

This package is the shared kernel of the architecture docs sites, `plugins/air/docs` and `build/docs`. `README.md`
explains the config, the provider, the checks, the wiring, and how to add a site.

- Keep a kernel module free of a site. Do not import from `plugins/air`, from `build/docs`, or from another site. A
  site gives its paths and its data through `site.config.ts`, its `ModelProvider`, and the parts of `createSite`.
- Do not put a site fact in the kernel. A path, a node kind, a topic, or a term goes into the site config.
- Import a kernel file by its relative path. Do not use the `@/` alias, because each site defines its own.
- Keep each module that a gate loads free of npm imports. Both gates run on Bazel bun without `node_modules`.
  `scripts/yamlCompat.ts` falls back to `Bun.YAML` there.
- Keep each shared dependency at the version that each site `package.json` names. `DocsSiteToolchainConsistencyTest`
  checks the three manifests and the Bazel pins.
- Keep the toolchain aliases in `//build`. The root `.bazelignore` lists `community`.
- Write the comments and the docs in Simplified Technical English. Keep a sentence at or under 25 words.
- The web skills for this stack are not bundled. Install them from
  [`.agents/skills/OPTIONAL.md`](../../../.agents/skills/OPTIONAL.md) before you change the interface.
- After a change, run `pnpm run test:scripts` and `pnpm run typecheck` here. Then run `pnpm run check` and
  `pnpm run build` in both sites. After a change of `src/` or `styles/`, also run `pnpm run test:e2e` in both sites.
