// The type ramp and `cn`: cn (tailwind-merge) reads a `text-*` class that it does not know as a size as a color, so
// `cn('text-muted-foreground', 'text-detail')` dropped the color. Each step of the ramp must be a size to cn.

import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { cn } from './lib/utils.ts'

const tailwindCss = readFileSync(new URL('../styles/tailwind.css', import.meta.url), 'utf8')
const steps = [...tailwindCss.matchAll(/^\s*--text-([\w-]+):\s*[\d.]+rem;/gm)].map(match => match[1])

test('the ramp is the Tailwind default steps from xs to 4xl, and the root keeps the 16px of the browser', () => {
  assert.deepEqual(steps, ['xs', 'sm', 'base', 'lg', 'xl', '2xl', '3xl', '4xl'])
  assert.deepEqual([...tailwindCss.matchAll(/^\s*--text-[\w-]+:\s*([\d.]+)rem;/gm)].map(match => Number(match[1]) * 16), [12, 14, 16, 18, 20, 24, 30, 36])
  assert.doesNotMatch(tailwindCss, /--text-\*:\s*initial/)
  assert.doesNotMatch(tailwindCss, /^\s*font-size:/m)
})

test('cn keeps a text color next to each step of the ramp, and merges two steps', () => {
  for (const step of steps) {
    assert.equal(cn('text-muted-foreground', `text-${step}`), `text-muted-foreground text-${step}`, step)
    assert.equal(cn('text-sm', `text-${step}`), `text-${step}`, step)
  }
})
