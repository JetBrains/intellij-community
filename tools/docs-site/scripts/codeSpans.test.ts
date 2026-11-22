import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { existsSync, symlinkSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { describe, it } from 'node:test'
import { addCodeSpanPaths, codeSpanPathsJson, gitIgnoredPaths, type IsSourceFile, resolveCodeSpanPaths, unresolvedCodeSpans } from './codeSpans.ts'
import { temporaryTree } from './tempTree.ts'

/** Each file that exists under `root` is a source file. */
function existsIn(root: string): IsSourceFile {
  return repoPath => existsSync(join(root, repoPath))
}

function git(root: string, ...args: string[]): void {
  const result = spawnSync('git', ['-C', root, ...args], { encoding: 'utf8' })
  assert.equal(result.status, 0, `git ${args.join(' ')}: ${result.stderr}`)
}

const HAS_GIT = spawnSync('git', ['--version']).status === 0

const FILES: Record<string, string> = {
  'build/readme.md': '# Build\n',
  'build/dev-build.json': '{}\n',
  'build/docs/guides/near.md': 'A guide.\n',
  'build/docs/guides/build/shadow.json': '{}\n',
  'build/shadow.json': '{}\n',
}

const BODY = [
  'The registry is `build/dev-build.json`, and `near.md` sits next to the guide.',
  'The doc folder wins for `build/shadow.json`, and `../../readme.md` is doc-relative by its form.',
  'A jar holds `META-INF/plugin.xml`.',
  '',
  '```',
  '`build/other.json` in a fence is code.',
  '```',
].join('\n')

const SCRIPTS: Record<string, string> = {
  'build/jpsModelToBazel.cmd': '',
  'build/BUILD.bazel': '',
  'MODULE.bazel': '',
  'community/build/intellij_dev.bzl': '',
  'community/build/dist-tools/crates/fscopy/src/lib.rs': '',
  'build/docs/guides/local.rs': '',
}

describe('unresolvedCodeSpans', () => {
  it('resolves a Starlark, Bazel, Rust, or script span, and keeps one that names no file', () => {
    const root = temporaryTree('code-spans-', SCRIPTS)
    const body = [
      'Run `./build/jpsModelToBazel.cmd`, read `build/BUILD.bazel`, `MODULE.bazel`, and `community/build/intellij_dev.bzl`.',
      'The copy is `community/build/dist-tools/crates/fscopy/src/lib.rs`, next to `local.rs` and `./local.rs`.',
      'A missing `../missing.rs`, `./tests.cmd`, and `bin/<base>.cmd` stay code.',
    ].join('\n')
    assert.deepEqual(unresolvedCodeSpans('', 'build/docs/guides/g.md', body, existsIn(root)), new Map([
      ['./build/jpsModelToBazel.cmd', 'build/jpsModelToBazel.cmd'],
      ['build/BUILD.bazel', 'build/BUILD.bazel'],
      ['MODULE.bazel', 'MODULE.bazel'],
      ['community/build/intellij_dev.bzl', 'community/build/intellij_dev.bzl'],
      ['community/build/dist-tools/crates/fscopy/src/lib.rs', 'community/build/dist-tools/crates/fscopy/src/lib.rs'],
      ['../missing.rs', null],
      ['./tests.cmd', null],
    ]))
  })

  it('names the repository path of a span that the doc folder does not resolve, or null', () => {
    const root = temporaryTree('code-spans-', FILES)
    assert.deepEqual(unresolvedCodeSpans('', 'build/docs/guides/g.md', BODY, existsIn(root)), new Map([
      ['build/dev-build.json', 'build/dev-build.json'],
      ['META-INF/plugin.xml', null],
    ]))
  })

  it('tries the content root of a plugin, then the repository root', () => {
    const root = temporaryTree('code-spans-', { 'plugins/air/backend/acp/TODO.md': '# Todo\n', 'community/build/X.kt': '' })
    assert.deepEqual(unresolvedCodeSpans('plugins/air', 'docs/note.md', 'See `backend/acp/TODO.md` and `community/build/X.kt`.', existsIn(root)), new Map([
      ['backend/acp/TODO.md', 'plugins/air/backend/acp/TODO.md'],
      ['community/build/X.kt', 'community/build/X.kt'],
    ]))
  })

  it('writes the spans by doc folder in sorted order', () => {
    const root = temporaryTree('code-spans-', FILES)
    const paths = new Map<string, Map<string, string | null>>()
    addCodeSpanPaths(paths, '', 'build/docs/guides/g.md', BODY, existsIn(root))
    addCodeSpanPaths(paths, '', 'build/readme.md', 'Nothing here.', existsIn(root))
    assert.equal(
      codeSpanPathsJson(paths, new Map([['a', new Map([['z.md', null]])]])),
      '{"a":{"z.md":null},"build/docs/guides":{"META-INF/plugin.xml":null,"build/dev-build.json":"build/dev-build.json"}}',
    )
  })

  it('counts a file that git ignores as absent, so a build output of the checkout changes no answer', { skip: !HAS_GIT }, () => {
    const root = temporaryTree('code-spans-', {
      '.gitignore': '/out/\nbuild/docs/generated/\n',
      'build/dev-build.json': '{}\n',
      'out/update-from-sources/update-ide.json': '{}\n',
      'build/docs/guides/generated/near.json': '{}\n',
      'build/docs/generated/kept.json': '{}\n',
    })
    git(root, 'init', '-q')
    git(root, 'add', '.gitignore', 'build/dev-build.json')
    // a tracked file under an ignore pattern is a source
    git(root, 'add', '-f', 'build/docs/generated/kept.json')
    const body = [
      'The saved choice is `out/update-from-sources/update-ide.json`, next to `build/dev-build.json`.',
      'A tracked `build/docs/generated/kept.json` and an untracked `generated/near.json` count.',
    ].join('\n')
    const paths = resolveCodeSpanPaths('', new Map([['build/docs/guides/g.md', body]]), root)
    assert.deepEqual(paths, new Map([['build/docs/guides', new Map([
      ['out/update-from-sources/update-ide.json', null],
      ['build/dev-build.json', 'build/dev-build.json'],
      ['build/docs/generated/kept.json', 'build/docs/generated/kept.json'],
    ])]]))
  })
})

describe('gitIgnoredPaths', () => {
  it('names the ignored paths only, and gives none outside a work tree', { skip: !HAS_GIT }, () => {
    const root = temporaryTree('code-spans-', { '.gitignore': 'out/\n', 'out/a.json': '', 'b.json': '' })
    // a temporary directory inside a work tree, such as a home directory under git, must not give an answer
    const ceiling = process.env.GIT_CEILING_DIRECTORIES
    process.env.GIT_CEILING_DIRECTORIES = dirname(root)
    try {
      assert.deepEqual(gitIgnoredPaths(root, ['out/a.json', 'b.json']), new Set())
    }
    finally {
      if (ceiling === undefined) {
        delete process.env.GIT_CEILING_DIRECTORIES
      }
      else {
        process.env.GIT_CEILING_DIRECTORIES = ceiling
      }
    }
    git(root, 'init', '-q')
    assert.deepEqual(gitIgnoredPaths(root, ['out/a.json', 'b.json']), new Set(['out/a.json']))
    assert.deepEqual(gitIgnoredPaths(root, []), new Set())
  })

  it('names no tracked file that an ignore rule matches, and no folder that holds one', { skip: !HAS_GIT }, () => {
    const root = temporaryTree('code-spans-', {
      '.gitignore': 'generated/\n',
      'generated/kept.json': '{}\n',
      'generated/[kept].json': '{}\n',
      'generated/local.json': '{}\n',
      'generated/sub/local.json': '{}\n',
    })
    git(root, 'init', '-q')
    git(root, 'add', '.gitignore')
    git(root, 'add', '-f', 'generated/kept.json', 'generated/[kept].json')
    const tracked = ['generated', 'generated/kept.json', 'generated/[kept].json']
    const untracked = ['generated/local.json', 'generated/sub', 'generated/sub/local.json']
    assert.deepEqual(gitIgnoredPaths(root, [...tracked, ...untracked]), new Set(untracked))
  })

  it('answers for a path below a symbolic link with the link', { skip: !HAS_GIT }, () => {
    const elsewhere = temporaryTree('code-spans-out-', { 'update-from-sources/update-ide.json': '{}\n' })
    const root = temporaryTree('code-spans-', { '.gitignore': '/out\n', 'real/a.json': '{}\n' })
    symlinkSync(elsewhere, join(root, 'out'))
    symlinkSync('real', join(root, 'linked'))
    git(root, 'init', '-q')
    git(root, 'add', '.gitignore', 'real/a.json', 'linked')
    const paths = ['out/update-from-sources/update-ide.json', 'linked/a.json', 'real/a.json']
    assert.deepEqual(gitIgnoredPaths(root, paths), new Set(['out/update-from-sources/update-ide.json']))
  })

  it('throws when git fails in a work tree', { skip: !HAS_GIT }, () => {
    const root = temporaryTree('code-spans-', { 'a.json': '' })
    git(root, 'init', '-q')
    // a path outside the work tree is a fatal error of git
    assert.throws(() => gitIgnoredPaths(root, ['../outside.json']), /git check-ignore in .* failed with the status 128/)
  })
})
