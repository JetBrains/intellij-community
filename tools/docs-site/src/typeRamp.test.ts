// The type ramp and `cn`: cn (tailwind-merge) reads a `text-*` class that it does not know as a size as a color, so
// `cn('text-muted-foreground', 'text-detail')` dropped the color. Each step of the ramp must be a size to cn.

import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { cn } from './lib/utils.ts'

const tailwindCss = readFileSync(new URL('../styles/tailwind.css', import.meta.url), 'utf8')
const steps = [...tailwindCss.matchAll(/^\s*--text-([\w-]+):\s*[\d.]+px;/gm)].map(match => match[1])

test('the ramp has one whole-pixel step per role, from 10px to 38px', () => {
  assert.deepEqual(steps, ['3xs', '2xs', 'xs', 'sm', 'md', 'base', 'lg', 'xl', '2xl', '3xl'])
  assert.deepEqual([...tailwindCss.matchAll(/^\s*--text-[\w-]+:\s*([\d.]+)px;/gm)].map(match => Number(match[1])), [10, 11, 12, 13, 14, 15, 17, 22, 28, 38])
})

test('cn keeps a text color next to each step of the ramp, and merges two steps', () => {
  for (const step of steps) {
    assert.equal(cn('text-muted-foreground', `text-${step}`), `text-muted-foreground text-${step}`, step)
    assert.equal(cn('text-sm', `text-${step}`), `text-${step}`, step)
  }
})
