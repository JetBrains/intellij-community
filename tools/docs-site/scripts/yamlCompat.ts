// YAML runtime compatibility. The docs scripts normally run under node with docs/node_modules
// installed, but the architecture gate (scripts/gate.ts) also runs under the repo-pinned bun
// (community/tools/bun.cmd) inside the Bazel test sandbox, where node_modules may be absent.
// Parsing falls back to the Bun.YAML built-in there; stringification (the npm `yaml` Document
// API, needed only when regenerating the committed files) stays npm-only.

let yamlModule: typeof import('yaml') | null
try {
  yamlModule = await import('yaml')
}
catch {
  yamlModule = null
}

export function parseYaml(text: string): unknown {
  if (yamlModule) {
    return yamlModule.parse(text, { prettyErrors: true })
  }
  const bunYaml = (globalThis as { Bun?: { YAML?: { parse: (text: string) => unknown } } }).Bun?.YAML
  if (bunYaml) {
    return bunYaml.parse(text)
  }
  throw new Error("no YAML parser available: the npm 'yaml' package is not installed and Bun.YAML is missing")
}
