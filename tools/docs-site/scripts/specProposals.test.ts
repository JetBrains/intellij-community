import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { proposalFindings } from './specProposals.ts'

const MARKED = '### One way in for a prompt (Proposed)\n\n- A prompt is a row.\n\n### Next\n\n- A thing.\n'

describe('the proposal rule', () => {
  it('passes a marked proposal and a pointer that names the mark', () => {
    const pointer = '- See [Other](other.spec.md), "One way in for a prompt (Proposed)".\n'
    assert.deepEqual(proposalFindings([{ path: 'a.spec.md', text: MARKED }, { path: 'b.spec.md', text: pointer }]), [])
  })

  it('finds a pointer to a proposal that does not say Proposed', () => {
    const pointer = '- A close keeps the row. See [Other](other.spec.md), "One way in for a prompt".\n'
    assert.deepEqual(proposalFindings([{ path: 'a.spec.md', text: MARKED }, { path: 'b.spec.md', text: pointer }]), [
      `b.spec.md:1: proposal: names the proposed section "One way in for a prompt" without ' (Proposed)'; write "One way in for a prompt (Proposed)", so the reader knows that the behavior is not built`,
    ])
  })

  it('finds a proposal that claims a test, and passes a test after the section ends', () => {
    const proved = '### One way in for a prompt (Proposed)\n\n- A prompt is a row.\n  [@test] ../x/YTest.kt\n'
    assert.deepEqual(proposalFindings([{ path: 'a.spec.md', text: proved }]), [
      `a.spec.md:4: proposal: a [@test] link stands in the proposed section "One way in for a prompt (Proposed)"; a proposal has no test, so remove the link, or build the code and drop ' (Proposed)'`,
    ])
    const after = `${MARKED}\n- A real rule.\n  [@test] ../x/YTest.kt\n`
    assert.deepEqual(proposalFindings([{ path: 'a.spec.md', text: after }]), [])
  })

  it('ends a section at a heading of its level or a higher one, and not at a lower one, and skips a fenced heading', () => {
    const text = '## Requirements\n\n### Later (Proposed)\n\n#### A detail\n\n[@test] ../x/YTest.kt\n\n```\n## Not a heading\n```\n\n## References\n\n[@test] ../x/ZTest.kt\n'
    assert.deepEqual(proposalFindings([{ path: 'a.spec.md', text }]).map(finding => finding.slice(0, finding.indexOf(': proposal'))), ['a.spec.md:7'])
  })
})
