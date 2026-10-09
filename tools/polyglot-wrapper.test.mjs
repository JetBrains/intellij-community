// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// The bash half of the polyglot wrappers, without Bazel: the Git Bash hand-off, the roots in both checkout layouts,
// the override, and the jscpd paths that need no Bazel. Run with `node --test community/tools/polyglot-wrapper.test.mjs`.

import {after, before, describe, it} from "node:test"
import {deepEqual, equal, match} from "node:assert/strict"
import {spawnSync} from "node:child_process"
import {chmod, copyFile, mkdir, mkdtemp, realpath, rm, writeFile} from "node:fs/promises"
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
