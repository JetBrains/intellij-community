import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { SOURCE_PATH_PATTERN, specTestFiles } from './sourcePaths.ts'

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

describe('specTestFiles', () => {
  it('resolves each [@test] line against the folder of the spec, unique and sorted', () => {
    const body = '# Spec\n\n[@test] ../../b/testSrc/BTest.kt\n  [@test] ../../a/testSrc/ATest.kt\nText [@test] x.kt\n[@test] ../../b/testSrc/BTest.kt\n'
    assert.deepEqual(specTestFiles('plugins/air', 'spec/core', body), ['plugins/air/a/testSrc/ATest.kt', 'plugins/air/b/testSrc/BTest.kt'])
  })

  it('leaves out a target that the filter refuses', () => {
    const body = '[@test] ../../a/testSrc/ATest.kt\n[@test] ../../gone/GoneTest.kt\n'
    const refused: string[] = []
    const files = specTestFiles('plugins/air', 'spec/core', body, (repoPath, target) => {
      if (repoPath.includes('/gone/')) {
        refused.push(target)
        return false
      }
      return true
    })
    assert.deepEqual(files, ['plugins/air/a/testSrc/ATest.kt'])
    assert.deepEqual(refused, ['../../gone/GoneTest.kt'])
  })
})
