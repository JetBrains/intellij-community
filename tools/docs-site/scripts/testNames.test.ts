import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { declaresTest, testLinkNames } from './testNames.ts'

describe('the test names of a [@test] link', () => {
  it('are the backticked names of the group after the path, also across a line break', () => {
    const text = '[@test] a/BTest.kt (`a sentence, with a comma`,\n  `bareName`)\n'
    assert.deepEqual(testLinkNames(text, '[@test] a/BTest.kt'.length), ['a sentence, with a comma', 'bareName'])
  })

  it('are none without a group, or with a group that does not close', () => {
    assert.deepEqual(testLinkNames('[@test] a/BTest.kt and `prose`\n', '[@test] a/BTest.kt'.length), [])
    assert.deepEqual(testLinkNames('[@test] a/BTest.kt (`open`\n', '[@test] a/BTest.kt'.length), [])
  })
})

describe('the declaration of a test', () => {
  it('in TypeScript is a test or an it call with the title, and no describe title or a prefix of a title', () => {
    const source = [
      `test.describe('the trace viewer', () => {`,
      `  test('a dropped zip opens on its first instruction', async () => {})`,
      `  it.skip("a cut-off run says where it stopped", () => {})`,
      `})`,
      `test('mismatched quotes", () => {})`,
    ].join('\n')
    assert.ok(declaresTest('trace.test.ts', source, 'a dropped zip opens on its first instruction'))
    assert.ok(declaresTest('trace.test.ts', source, 'a cut-off run says where it stopped'))
    assert.ok(!declaresTest('trace.test.ts', source, 'a dropped zip opens'), 'a prefix of a title')
    assert.ok(!declaresTest('trace.test.ts', source, 'the trace viewer'), 'a describe title')
    assert.ok(!declaresTest('trace.test.ts', source, 'mismatched quotes'), 'mismatched quotes')
  })

  it('in Rust is a function under #[test] or #[tokio::test], and no helper or a prefix of a name', () => {
    const source = [
      '#[test]',
      'fn the_ladder_reads_x() {}',
      '',
      '#[tokio::test(flavor = "multi_thread", worker_threads = 2)]',
      'async fn a_lease_outlives_its_caller() {}',
      '',
      '#[test]',
      '#[ignore = "the child process of the spawn tests"]',
      'fn spawned_child() {}',
      '',
      '#[tokio::test]',
      '#[cfg(unix)]',
      'async fn a_signal_reaches_the_group() {}',
      '',
      'fn replay_helper(x: u32) {}',
    ].join('\n')
    assert.ok(declaresTest('tests.rs', source, 'the_ladder_reads_x'))
    assert.ok(declaresTest('tests.rs', source, 'a_lease_outlives_its_caller'), 'tokio with arguments')
    assert.ok(declaresTest('tests.rs', source, 'spawned_child'), 'an attribute after #[test]')
    assert.ok(declaresTest('tests.rs', source, 'a_signal_reaches_the_group'), 'bare tokio, then cfg')
    assert.ok(!declaresTest('tests.rs', source, 'replay_helper'), 'a helper')
    assert.ok(!declaresTest('tests.rs', source, 'the_ladder_reads'), 'a prefix of a name')
  })

  it('in Kotlin is a backticked sentence name or a bare name after fun', () => {
    const source = '@Test fun `a sentence name`() {}\n@Test fun bareName() {}\n'
    assert.ok(declaresTest('SomeTest.kt', source, 'a sentence name'))
    assert.ok(declaresTest('SomeTest.kt', source, 'bareName'))
    assert.ok(!declaresTest('SomeTest.kt', source, 'missingName'))
  })

  it('in Python and Starlark is a def, and no class name or a prefix of a name', () => {
    const python = 'class HookTest(unittest.TestCase):\n    def test_fail_open(self) -> None:\n        pass\n'
    assert.ok(declaresTest('test_hook.py', python, 'test_fail_open'))
    assert.ok(!declaresTest('test_hook.py', python, 'test_fail'), 'a prefix of a name')
    assert.ok(!declaresTest('test_hook.py', python, 'HookTest'), 'a class name')
    const starlark = 'def _idea_marker_test_impl(ctx):\n    pass\n\ndef idea_marker_test(name):\n    pass\n'
    assert.ok(declaresTest('marker_test.bzl', starlark, 'idea_marker_test'))
    assert.ok(!declaresTest('marker_test.bzl', starlark, 'idea_marker'), 'a prefix of a name')
  })

  it('in JSON is the value of a name field, and no prefix of it or the value of another field', () => {
    const source = '{"profiles": [{"name": "Air loads few classes", "fixture": "bare"}]}'
    assert.ok(declaresTest('startup.json', source, 'Air loads few classes'))
    assert.ok(!declaresTest('startup.json', source, 'Air loads few'), 'a prefix of a name')
    assert.ok(!declaresTest('startup.json', source, 'bare'), 'the value of another field')
  })
})
