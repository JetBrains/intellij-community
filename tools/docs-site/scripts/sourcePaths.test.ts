import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { docPathOfHarvestedFile, docPathOfRepoPath, harvestedFileOf, requirementGroup, resolveRepoPath, resolveSpecPath, SOURCE_PATH_PATTERN, specIdOfRepoPath, specTestFiles, TEST_LINK_LINE, testLinkNames } from './sourcePaths.ts'

describe('SOURCE_PATH_PATTERN', () => {
  it('takes a Starlark, Bazel, Rust, script, module, properties, or JSON path', () => {
    for (const text of [
      'community/build/intellij_dev.bzl',
      'build/BUILD.bazel',
      'MODULE.bazel',
      'community/build/dist-tools/crates/fscopy/src/lib.rs',
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

describe('a [@test] line with test names', () => {
  it('gives the path and the names in backticks, and a line without names gives the path alone', () => {
    const body = '  [@test] build/testSrc/ATest.kt (`reads the cache`, `writes nothing`)\n[@test] build/testSrc/BTest.kt\n'
    const matches = [...body.matchAll(TEST_LINK_LINE)]
    assert.deepEqual(matches.map(match => match[2]), ['build/testSrc/ATest.kt', 'build/testSrc/BTest.kt'])
    assert.deepEqual(testLinkNames(matches[0][3]), ['reads the cache', 'writes nothing'])
    assert.deepEqual(testLinkNames(matches[1][3]), [])
  })

  it('resolves from the repository root in an OpenSpec root, and from the spec folder otherwise', () => {
    assert.equal(resolveSpecPath('plugins/air', 'spec/core', true, 'build/testSrc/ATest.kt'), 'build/testSrc/ATest.kt')
    assert.equal(resolveSpecPath('plugins/air', 'spec/core', false, '../../a/ATest.kt'), 'plugins/air/a/ATest.kt')
    const body = '[@test] build/testSrc/ATest.kt (`a test`)\n'
    assert.deepEqual(specTestFiles('', 'build/openspec/specs/cache', body, () => true, true), ['build/testSrc/ATest.kt'])
  })
})

describe('the spec id of a path', () => {
  const specs = { roots: ['build/spec', 'build'], suffix: '.spec.md', openspec: { roots: ['build/openspec'] } }

  it('is the capability under an OpenSpec root, nested or flat', () => {
    assert.equal(specIdOfRepoPath(specs, 'build/openspec/specs/cache/spec.md'), 'cache')
    assert.equal(specIdOfRepoPath(specs, 'build/openspec/specs/sessions/local-launch/spec.md'), 'sessions/local-launch')
    assert.equal(specIdOfRepoPath(specs, 'build/openspec/specs/spec.md'), null)
    assert.equal(specIdOfRepoPath(specs, 'build/openspec/changes/a/specs/cache/spec.md'), null)
  })

  it('is the path below the first suffix root that holds the file, without the suffix', () => {
    assert.equal(specIdOfRepoPath(specs, 'build/spec/composition.spec.md'), 'composition')
    assert.equal(specIdOfRepoPath(specs, 'build/fix-exported-dep.spec.md'), 'fix-exported-dep')
    assert.equal(specIdOfRepoPath(specs, 'build/docs/README.md'), null)
  })
})

describe('the group of a requirement heading', () => {
  it('is the text before the first colon after the prefix, or null', () => {
    assert.equal(requirementGroup('Requirement: The three caches: A local build reads only'), 'The three caches')
    assert.equal(requirementGroup('Requirement: `brd`: The command prints JSON'), '`brd`')
    assert.equal(requirementGroup('Requirement: A rule without a group'), null)
    assert.equal(requirementGroup('Scenario: A case'), null)
  })
})

describe('a doc path outside the content root', () => {
  const design = 'community/tools/vm/openspec/changes/archive/2026-11-18-one-engine/design.md'

  it('is the content-relative path inside the content root, and climbs with .. segments outside it', () => {
    assert.equal(docPathOfRepoPath('plugins/air', 'plugins/air/docs/a.md'), 'docs/a.md')
    assert.equal(docPathOfRepoPath('plugins/air', design), `../../${design}`)
    assert.equal(docPathOfRepoPath('plugins/air', 'plugins/other/a.md'), '../other/a.md')
    assert.equal(docPathOfRepoPath('.', design), design)
  })

  it('is a base folder that a relative link resolves against', () => {
    const baseDir = docPathOfRepoPath('plugins/air', design).slice(0, -'/design.md'.length)
    assert.equal(resolveRepoPath('plugins/air', baseDir, '../../../../docs/vm-ui-tests.md'), 'community/tools/vm/docs/vm-ui-tests.md')
    assert.equal(resolveRepoPath('plugins/air', baseDir, '../../../../../../../plugins/air/docs/a.md'), 'plugins/air/docs/a.md')
  })

  it('keeps its harvested file inside the folder, and the file gives the doc path back', () => {
    const docPath = docPathOfRepoPath('plugins/air', design)
    assert.equal(harvestedFileOf(docPath), `__parent__/__parent__/${design}`)
    assert.equal(docPathOfHarvestedFile(harvestedFileOf(docPath)), docPath)
    assert.equal(harvestedFileOf('docs/a.md'), 'docs/a.md')
  })
})
