# Docs-site kernel

This package is the shared kernel of the architecture docs sites. `README.md` names the consumers, the wiring,
and the import convention.

- Keep a kernel module free of a site. Do not import from `plugins/air` or from another site. A site gives its
  paths and its data as arguments.
- Import a kernel file by its relative path. Do not use the `@/` alias, because each site defines its own.
- Keep the gate path of `scripts/` free of npm imports. The Air gate runs on Bazel bun without `node_modules`.
  `yamlCompat.ts` falls back to `Bun.YAML` there.
- Keep each shared dependency at the version that each site `package.json` names.
- Write the comments and the docs in Simplified Technical English. Keep a sentence at or under 25 words.
- The web skills for this stack are not bundled. Install them from
  [`.agents/skills/OPTIONAL.md`](../../../.agents/skills/OPTIONAL.md) before you change the interface.
- After a change, run `pnpm run test:scripts` and `pnpm run typecheck` here. Then run the checks of each site.
