import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { once } from './checkRun.ts'

describe('a check of a run', () => {
  it('runs once and returns the first result to each later call', () => {
    let runs = 0
    const check = once(() => ({ run: ++runs }))
    const first = check()
    assert.equal(check(), first)
    assert.equal(runs, 1)
  })

  it('keeps no result when it throws, so the next call runs it again', () => {
    let runs = 0
    const check = once(() => {
      runs++
      throw new Error(`finding ${runs}`)
    })
    assert.throws(check, /finding 1/)
    assert.throws(check, /finding 2/)
  })
})
