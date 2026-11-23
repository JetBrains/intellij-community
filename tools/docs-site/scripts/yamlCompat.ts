// YAML runtime compatibility. On bun, a docs script parses YAML with the Bun.YAML built-in. The gate of a site
// (scripts/gate.ts) runs on the bun that Bazel declares, with no node_modules, so a script on bun reads YAML the same
// way with and without the installed packages. On node, a docs script parses with the npm `yaml` package of the site.

const bunYaml = (globalThis as { Bun?: { YAML?: { parse: (text: string) => unknown } } }).Bun?.YAML

let yamlModule: typeof import('yaml') | null = null
if (bunYaml === undefined) {
  try {
    yamlModule = await import('yaml')
  }
  catch {
    yamlModule = null
  }
}

export function parseYaml(text: string): unknown {
  if (bunYaml) {
    return bunYaml.parse(text)
  }
  if (yamlModule) {
    return yamlModule.parse(text, { prettyErrors: true })
  }
  throw new Error("no YAML parser available: the npm 'yaml' package is not installed and Bun.YAML is missing")
}
