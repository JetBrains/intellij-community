import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { CHECK_FAILED_EXIT_CODE, gateExitCode, GATE_SENTINEL, INFRASTRUCTURE_EXIT_CODE } from './gate.ts'
import { OpenSpecInfrastructureError } from './openspec.ts'

/** Runs the gate over `body`, and answers the exit code and the written lines. */
async function run(body: () => Promise<readonly string[]>): Promise<{ code: number, lines: string[] }> {
  const lines: string[] = []
  const code = await gateExitCode(body, line => lines.push(line))
  return { code, lines }
}

describe('the gate protocol', () => {
  it('writes the sentinel before the body runs, then the records, and exits 0', async () => {
    const written: string[] = []
    const code = await gateExitCode(async () => {
      assert.deepEqual(written, [GATE_SENTINEL])
      return ['spec\ta', 'spec\tb']
    }, line => written.push(line))
    assert.equal(code, 0)
    assert.deepEqual(written, [GATE_SENTINEL, 'spec\ta\nspec\tb'])
  })

  it('writes the report of a failed check and exits 3', async () => {
    const { code, lines } = await run(async () => {
      throw new Error('a.md:4: broken link')
    })
    assert.equal(code, CHECK_FAILED_EXIT_CODE)
    assert.equal(lines[0], GATE_SENTINEL)
    assert.match(lines[1], /^Error: a\.md:4: broken link/)
  })

  it('exits 4 when the OpenSpec CLI gives no report', async () => {
    const { code, lines } = await run(async () => {
      throw new OpenSpecInfrastructureError('openspec: no report')
    })
    assert.equal(code, INFRASTRUCTURE_EXIT_CODE)
    assert.match(lines[1], /openspec: no report/)
  })
})
