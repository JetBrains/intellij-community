// The kernel stays free of a site: no kernel module imports from a site directory or through the `@/` alias of a
// site. A site gives its paths and its data to the kernel as arguments (README.md, AGENTS.md).

import assert from 'node:assert/strict'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative } from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'

const kernelRoot = dirname(dirname(fileURLToPath(import.meta.url)))

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).sort().flatMap(entry => {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) {
      return entry === 'node_modules' ? [] : sourceFiles(path)
    }
    return /\.(ts|tsx)$/.test(entry) ? [path] : []
  })
}

/** The module specifiers of the static imports, the re-exports, and the dynamic imports of `source`. */
function specifiers(source: string): string[] {
  return [...source.matchAll(/(?:\bfrom\s+|\bimport\s*\(\s*|^\s*import\s+)(['"])([^'"\n]+)\1/gm)].map(match => match[2])
}

test('no kernel module imports a site', () => {
  const self = fileURLToPath(import.meta.url)
  // this file holds the forbidden forms as the fixture of the scan test
  const offenders = ['src', 'scripts', 'e2e', 'vite'].flatMap(dir => sourceFiles(join(kernelRoot, dir))).filter(file => file !== self).flatMap(file => {
    const bad = specifiers(readFileSync(file, 'utf8'))
      .filter(specifier => specifier.startsWith('@/') || specifier.includes('plugins/air') || specifier.includes('../../../plugins'))
    return bad.map(specifier => `${relative(kernelRoot, file)}: ${specifier}`)
  })
  assert.deepEqual(offenders, [])
})

test('the import scan sees the import forms', () => {
  assert.deepEqual(specifiers(`import a from './a'\nexport { b } from "../b"\nconst c = lazy(() => import('@/c'))\nimport './d.css'\n`), ['./a', '../b', '@/c', './d.css'])
})
