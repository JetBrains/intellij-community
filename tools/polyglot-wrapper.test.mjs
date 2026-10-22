// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// The bash half of the polyglot wrappers, without Bazel: the Git Bash hand-off, the roots in both checkout layouts,
// the override, the build step against a stand-in `bazel.cmd`, and the jscpd paths that need no Bazel. The event file
// reader of the CMD half runs when a PowerShell is on PATH.
// Run with `node --test community/tools/polyglot-wrapper.test.mjs`.

import {after, before, describe, it} from "node:test"
import {deepEqual, equal, match} from "node:assert/strict"
import {spawnSync} from "node:child_process"
import {existsSync} from "node:fs"
import {chmod, copyFile, mkdir, mkdtemp, readdir, realpath, rm, writeFile} from "node:fs/promises"
import {tmpdir} from "node:os"
import {dirname, join} from "node:path"
import process from "node:process"
import {fileURLToPath} from "node:url"

const toolsDir = dirname(fileURLToPath(import.meta.url))

/** @type {string} */
let scratch
/** A directory first on PATH that makes bash believe it runs in Git Bash. */
let gitBashStubs

/**
 * @param {string} dir
 * @param {string} name
 * @param {string} body
 */
async function writeExecutable(dir, name, body) {
  const path = join(dir, name)
  await writeFile(path, `#!/bin/sh\n${body}\n`)
  await chmod(path, 0o755)
  return path
}

/**
 * @param {string[]} args
 * @param {{cwd?: string, env?: Record<string, string>}} [options]
 */
function bash(args, options = {}) {
  const env = {...process.env, ...options.env}
  for (const name of ["JSCPD_BIN", "AIR_BT_BIN", "AIR_VM_BIN", "AIR_TRACE_BIN"]) {
    if (!(name in (options.env ?? {}))) {
      delete env[name]
    }
  }
  const result = spawnSync("bash", args, {cwd: options.cwd ?? toolsDir, env, encoding: "utf8"})
  return {status: result.status, stdout: result.stdout, stderr: result.stderr}
}

before(async () => {
  scratch = await realpath(await mkdtemp(join(tmpdir(), "polyglot-wrapper-")))
  gitBashStubs = join(scratch, "git-bash")
  await mkdir(gitBashStubs)
  await writeExecutable(gitBashStubs, "uname", 'echo "MINGW64_NT-10.0-26100"')
  await writeExecutable(gitBashStubs, "cygpath", 'echo "W:$2"')
  await writeExecutable(gitBashStubs, "cmd.exe", 'for a in "$@"; do printf "%s\\n" "$a"; done')
})

after(async () => {
  await rm(scratch, {recursive: true, force: true})
})

describe("the Git Bash hand-off", () => {
  for (const wrapper of ["jscpd.cmd", "bt.cmd"]) {
    it(`${wrapper} hands its arguments to the CMD half through cmd.exe`, () => {
      const script = join(toolsDir, wrapper)
      const result = bash([script, "a", "b c"], {env: {PATH: `${gitBashStubs}:${process.env.PATH}`}})
      equal(result.status, 0, result.stderr)
      deepEqual(result.stdout.split("\n").filter(Boolean), ["//c", "call", `W:${script}`, "a", "b c"])
    })
  }
})

describe("the roots", () => {
  /**
   * @param {string} cwd the checkout root to run from
   * @param {string} selfDir the wrapper directory, relative to cwd
   */
  async function findRoots(cwd, selfDir) {
    await mkdir(join(cwd, selfDir), {recursive: true})
    await copyFile(join(toolsDir, "polyglot-wrapper.sh"), join(cwd, selfDir, "polyglot-wrapper.sh"))
    const script = `. "${selfDir}/polyglot-wrapper.sh"; polyglot_find_roots "${selfDir}"; printf "%s\\n%s\\n%s\\n" "$community_root" "$root" "$label_prefix"`
    const result = bash(["-c", script], {cwd})
    equal(result.status, 0, result.stderr)
    const [communityRoot, root, labelPrefix] = result.stdout.split("\n")
    return {communityRoot, root, labelPrefix}
  }

  it("a Community checkout is its own root", async () => {
    const checkout = join(scratch, "community-only")
    deepEqual(await findRoots(checkout, "tools"), {communityRoot: checkout, root: checkout, labelPrefix: "//"})
  })

  it("an Ultimate checkout is the parent, and the label goes through @community", async () => {
    const checkout = join(scratch, "ultimate")
    await mkdir(checkout, {recursive: true})
    await writeFile(join(checkout, "MODULE.bazel"), "")
    await writeFile(join(checkout, "bazel.cmd"), "")
    const expected = {communityRoot: join(checkout, "community"), root: checkout, labelPrefix: "@community//"}
    deepEqual(await findRoots(checkout, "community/tools"), expected)
  })

  it("an unrelated community directory next to the checkout does not make it Ultimate", async () => {
    const parent = join(scratch, "decoy")
    await mkdir(join(parent, "community"), {recursive: true})
    await writeFile(join(parent, "MODULE.bazel"), "")
    await writeFile(join(parent, "bazel.cmd"), "")
    const checkout = join(parent, "other")
    deepEqual(await findRoots(parent, "other/tools"), {communityRoot: checkout, root: checkout, labelPrefix: "//"})
  })
})

describe("the override", () => {
  it("jscpd runs JSCPD_BIN with the arguments and nothing else", async () => {
    const binary = await writeExecutable(scratch, "fake-jscpd", 'for a in "$@"; do printf "%s\\n" "$a"; done')
    const result = bash([join(toolsDir, "jscpd.cmd"), "x", "y z"], {env: {JSCPD_BIN: binary}})
    equal(result.status, 0, result.stderr)
    deepEqual(result.stdout.split("\n").filter(Boolean), ["x", "y z"])
  })

  it("jscpd exits 78 for a JSCPD_BIN that is not an executable file", () => {
    const result = bash([join(toolsDir, "jscpd.cmd")], {env: {JSCPD_BIN: join(scratch, "missing")}})
    equal(result.status, 78)
    match(result.stderr, /^jscpd: JSCPD_BIN is not an executable file: /)
  })

  it("bt keeps its own exit code, 6", () => {
    const result = bash([join(toolsDir, "bt.cmd")], {env: {AIR_BT_BIN: join(scratch, "missing")}})
    equal(result.status, 6)
    match(result.stderr, /^bt: AIR_BT_BIN is not an executable file: /)
  })
})

describe("jscpd without Bazel", () => {
  it("exits 78 when the pin file has no version line", async () => {
    const checkout = join(scratch, "no-version")
    await mkdir(join(checkout, "tools"), {recursive: true})
    for (const name of ["jscpd.cmd", "polyglot-wrapper.sh"]) {
      await copyFile(join(toolsDir, name), join(checkout, "tools", name))
    }
    await writeFile(join(checkout, "tools", "jscpd.MODULE.bazel"), "# no pin here\n")
    const result = bash([join(checkout, "tools", "jscpd.cmd"), "--version"], {cwd: checkout})
    equal(result.status, 78)
    match(result.stderr, /^jscpd: found no JSCPD_VERSION line in /)
  })
})

describe("the build step", () => {
  /**
   * An Ultimate-shaped checkout whose `bazel.cmd` is a stand-in: it prints one line of noise on stderr, and it
   * writes the event file that its `--build_event_json_file=` argument names, with the content of
   * `STUB_BAZEL_EVENTS`. `STUB_BAZEL_FAIL` makes it exit 1 before the event file.
   * @param {string} name
   * @param {string[]} wrappers
   */
  async function checkout(name, wrappers) {
    const root = join(scratch, name)
    const tools = join(root, "community", "tools")
    await mkdir(tools, {recursive: true})
    await writeFile(join(root, "MODULE.bazel"), "")
    await writeExecutable(root, "bazel.cmd", [
      'echo "bazel noise" >&2',
      '[ -n "${STUB_BAZEL_FAIL:-}" ] && exit 1',
      'for a in "$@"; do case "$a" in --build_event_json_file=*) events="${a#--build_event_json_file=}" ;; esac; done',
      'printf "%s\\n" "$STUB_BAZEL_EVENTS" > "$events"',
    ].join("\n"))
    for (const wrapper of [...wrappers, "polyglot-wrapper.sh"]) {
      await copyFile(join(toolsDir, wrapper), join(tools, wrapper))
    }
    return {root, tools}
  }

  /**
   * The stand-in for the binary a build makes. Its base name is what the reader matches, so it is the name of the
   * real binary, in a directory of the checkout `name`.
   * @param {string} name
   * @param {string} basename
   * @param {string} body
   */
  async function fakeBinary(name, basename, body) {
    const dir = join(scratch, name, "bin")
    await mkdir(dir, {recursive: true})
    return writeExecutable(dir, basename, body)
  }

  /**
   * One `namedSetOfFiles` record that names the given files, as `--nobuild_event_json_file_path_conversion` writes it.
   * @param {string[]} paths
   */
  function namedSetOfFiles(paths) {
    const files = paths.map((path) => `{"name":"${path.split("/").pop()}","uri":"file://${path}"}`)
    return `{"id":{"namedSet":{"id":"0"}},"namedSetOfFiles":{"files":[${files.join(",")}]}}`
  }

  it("polyglot_built_file answers the last file with the base name, across records", async () => {
    const events = join(scratch, "events.json")
    await writeFile(events, [
      namedSetOfFiles(["/out/a/bt.d", "/out/a/bt"]),
      '{"id":{"progress":{}},"progress":{"stderr":"\\"uri\\":\\"file:///decoy/bt\\" is text"}}',
      namedSetOfFiles(["/out/b/bt"]),
    ].join("\n"))
    const script = `. "${toolsDir}/polyglot-wrapper.sh"; polyglot_built_file "${events}" bt; polyglot_built_file "${events}" vm`
    const result = bash(["-c", script])
    equal(result.status, 0, result.stderr)
    deepEqual(result.stdout.split("\n"), ["/out/b/bt", "", ""])
  })

  it("bt runs the binary the event file names, with the build noise on stderr", async () => {
    const {root, tools} = await checkout("build-ok", ["bt.cmd"])
    const binary = await fakeBinary("build-ok", "bt", 'echo "$BUILD_WORKSPACE_DIRECTORY"; for a in "$@"; do printf "%s\\n" "$a"; done')
    const result = bash([join(tools, "bt.cmd"), "x", "y z"], {env: {STUB_BAZEL_EVENTS: namedSetOfFiles([binary])}})
    equal(result.status, 0, result.stderr)
    deepEqual(result.stdout.split("\n").filter(Boolean), [root, "x", "y z"])
    equal(result.stderr, "bazel noise\n")
  })

  it("bt exits 6 when the build fails, and 6 when the event file names no binary", async () => {
    const {tools} = await checkout("build-fails", ["bt.cmd"])
    const failed = bash([join(tools, "bt.cmd")], {env: {STUB_BAZEL_FAIL: "1", STUB_BAZEL_EVENTS: ""}})
    equal(failed.status, 6)
    match(failed.stderr, /^bazel noise\nbt: could not build @community\/\/tools\/bt:bt; see the bazel output above\n$/)
    const unresolved = bash([join(tools, "bt.cmd")], {env: {STUB_BAZEL_EVENTS: namedSetOfFiles(["/out/other"])}})
    equal(unresolved.status, 6)
    match(unresolved.stderr, /bt: could not resolve bt from @community\/\/tools\/bt:bt \(got ''\)\n$/)
  })

  it("vm keeps the build noise off a piped stderr, and shows it when the build fails", async () => {
    const {tools} = await checkout("build-log", ["vm.cmd"])
    const binary = await fakeBinary("build-log", "vm", 'echo controller')
    const quiet = bash([join(tools, "vm.cmd")], {env: {STUB_BAZEL_EVENTS: namedSetOfFiles([binary])}})
    equal(quiet.status, 0, quiet.stderr)
    equal(quiet.stdout, "controller\n")
    equal(quiet.stderr, "")
    const failed = bash([join(tools, "vm.cmd")], {env: {STUB_BAZEL_FAIL: "1", STUB_BAZEL_EVENTS: ""}})
    equal(failed.status, 78)
    equal(failed.stderr, "bazel noise\nvm: could not build @community//tools/vm:vm; see the bazel output above\n")
  })

  it("leaves no event file or log behind", async () => {
    const {root, tools} = await checkout("build-clean", ["bt.cmd", "vm.cmd"])
    const bt = await fakeBinary("build-clean", "bt", 'exit 0')
    const vm = await fakeBinary("build-clean", "vm", 'exit 0')
    bash([join(tools, "bt.cmd")], {env: {STUB_BAZEL_EVENTS: namedSetOfFiles([bt])}})
    bash([join(tools, "vm.cmd")], {env: {STUB_BAZEL_EVENTS: namedSetOfFiles([vm])}})
    bash([join(tools, "vm.cmd")], {env: {STUB_BAZEL_FAIL: "1", STUB_BAZEL_EVENTS: ""}})
    deepEqual(await readdir(join(root, "out", "air")), [])
  })
})

describe("the event file reader of the CMD half", () => {
  /** The first PowerShell on PATH: `pwsh` on any host, `powershell.exe` on a Windows host. */
  function powerShell() {
    const directories = (process.env.PATH ?? "").split(process.platform === "win32" ? ";" : ":").filter(Boolean)
    for (const name of ["pwsh", "pwsh.exe", "powershell.exe"]) {
      for (const directory of directories) {
        const candidate = join(directory, name)
        if (existsSync(candidate)) {
          return candidate
        }
      }
    }
    return undefined
  }

  const shell = powerShell()
  const skip = shell === undefined ? "no pwsh or powershell.exe on PATH" : false

  /**
   * @param {string} events
   * @param {string} name
   */
  function read(events, name) {
    const script = join(toolsDir, "polyglot-built-file.ps1")
    const args = ["-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script, events, name]
    const result = spawnSync(/** @type {string} */ (shell), args, {encoding: "utf8"})
    return {status: result.status, stdout: result.stdout, stderr: result.stderr}
  }

  it("answers the last file with the base name as a local path, across records", {skip}, async () => {
    const events = join(scratch, "reader-events.json")
    await writeFile(events, [
      '{"id":{"namedSet":{"id":"0"}},"namedSetOfFiles":{"files":[{"name":"bt.d","uri":"file:///C:/out/a/bt.d"},{"name":"bt.exe","uri":"file:///C:/out/a/bt.exe"}]}}',
      '{"id":{"progress":{}},"progress":{"stderr":"\\"uri\\":\\"file:///C:/decoy/bt.exe\\" is text"}}',
      '{"id":{"namedSet":{"id":"1"}},"namedSetOfFiles":{"files":[{"name":"bt.exe","uri":"file:///C:/out/b/bt.exe"}]}}',
    ].join("\n"))
    const found = read(events, "bt.exe")
    equal(found.status, 0, found.stderr)
    // `System.Uri` answers the path of the host: `C:\out\b\bt.exe` on Windows, `/C:/out/b/bt.exe` elsewhere.
    match(found.stdout.trim().replaceAll("\\", "/"), /^\/?C:\/out\/b\/bt\.exe$/)
    const missing = read(events, "vm.exe")
    equal(missing.status, 0, missing.stderr)
    equal(missing.stdout.trim(), "")
  })

  it("fails on an event file that is not there", {skip}, () => {
    const result = read(join(scratch, "no-such-events.json"), "bt.exe")
    equal(result.status, 1)
    match(result.stderr, /no-such-events\.json/)
  })
})
