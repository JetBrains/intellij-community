import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { SOURCE_PATH_PATTERN } from './sourcePaths.ts'

describe('SOURCE_PATH_PATTERN', () => {
  it('takes a Starlark, Bazel, Rust, script, module, properties, or JSON path', () => {
    for (const text of [
      'community/build/intellij_dev.bzl',
      'build/BUILD.bazel',
      'MODULE.bazel',
      'community/build/dev-dist-tools/crates/fscopy/src/lib.rs',
      './build/jpsModelToBazel.cmd',
      'build/docs/scripts/harvest.ts',
      'bin/idea.properties',
      'build/dev-build.json#L3',
    ]) {
      assert.ok(SOURCE_PATH_PATTERN.test(text), text)
    }
  })

  it('refuses a command line, a Bazel label, and a placeholder', () => {
    for (const text of ['./bazel.cmd run //:format.check', '//build:idea', 'bin/<base>.cmd', 'build.rsx']) {
      assert.ok(!SOURCE_PATH_PATTERN.test(text), text)
    }
  })
})
