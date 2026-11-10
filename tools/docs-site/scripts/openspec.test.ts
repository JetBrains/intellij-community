import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { checkOpenSpec, OpenSpecInfrastructureError, openspecFindings, type OpenSpecConfig } from './openspec.ts'
import { temporaryTree } from './tempTree.ts'
import { testSiteConfig } from './testSiteConfig.ts'

function config(roots: string[] = ['os/openspec']): OpenSpecConfig {
  return { specs: { ...testSiteConfig().specs, openspec: { roots, templateSections: [] } } }
}

/** A fake CLI: a script for the runtime of this test that prints `stdout` and exits with `code`. */
function fakeCli(stdout: string, code: number = 0): string[] {
  return [process.execPath, '-e', `process.stdout.write(${JSON.stringify(stdout)}); process.exitCode = ${code}`]
}

const VALID_REPORT = JSON.stringify({ items: [{ id: 'cache', type: 'spec', valid: true, issues: [] }] })
const INVALID_REPORT = JSON.stringify({
  items: [
    { id: 'cache', type: 'spec', valid: false, issues: [{ level: 'ERROR', path: 'requirements[0]', message: 'Requirement must have at least one scenario' }] },
    { id: 'one', type: 'change', valid: false, issues: [] },
  ],
})

describe('the OpenSpec check', () => {
  it('counts the items of both validate runs and finds nothing in a valid report', () => {
    const root = temporaryTree('openspec-', { 'os/openspec/config.yaml': 'schema: spec-driven\n' })
    assert.deepEqual(openspecFindings(config(), fakeCli(VALID_REPORT), root), { findings: [], items: 2 })
    assert.equal(checkOpenSpec(config(), fakeCli(VALID_REPORT), root), 2)
  })

  it('names the root, the item, the level and the message of each issue, and an invalid item without an issue', () => {
    const root = temporaryTree('openspec-', { 'os/openspec/config.yaml': 'schema: spec-driven\n' })
    const { findings } = openspecFindings(config(), fakeCli(`noise before the report\n${INVALID_REPORT}`, 1), root)
    assert.deepEqual(findings.slice(0, 2), [
      'os/openspec: spec cache: ERROR: Requirement must have at least one scenario (requirements[0])',
      'os/openspec: change one: ERROR: invalid',
    ])
    assert.throws(() => checkOpenSpec(config(), fakeCli(INVALID_REPORT, 1), root), /^Error: openspec: 4 finding\(s\)/)
  })

  it('throws an infrastructure error when the CLI gives no report', () => {
    const root = temporaryTree('openspec-', { 'os/openspec/config.yaml': 'schema: spec-driven\n' })
    assert.throws(() => openspecFindings(config(), fakeCli('not a report', 2), root), OpenSpecInfrastructureError)
    assert.throws(() => openspecFindings(config(), ['/no/such/program'], root), OpenSpecInfrastructureError)
  })

  it('refuses a root that is not a folder named openspec, because the CLI finds only that name', () => {
    const root = temporaryTree('openspec-', { 'os/specs/config.yaml': 'schema: spec-driven\n' })
    assert.throws(() => openspecFindings(config(['os/specs']), fakeCli(VALID_REPORT), root), /is not a folder named openspec/)
  })
})
