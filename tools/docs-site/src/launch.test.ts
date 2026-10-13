import assert from 'node:assert/strict'
import { test } from 'node:test'
import { initialLaunchEntry, rankLaunchEntries } from './launch.ts'
import type { LaunchEntry } from './siteData.ts'

function entry(name: string, target: string): LaunchEntry {
  return { name, target, command: `./bazel.cmd run ${target}`, half: target.startsWith('@community') ? 'community' : 'ultimate', configurationFile: `${name}.xml` }
}

const ENTRIES = [
  entry('Android Studio', '//build:android_studio'),
  entry('GoLand', '//build:goland'),
  entry('IDEA', '//build:idea'),
  entry('IDEA (air)', '//build:idea_air'),
  entry('IDEA Community', '//build:idea_community'),
  entry('IDEA Community', '@community//build:idea_community'),
  entry('My IDEA', '//build:my_idea'),
]

const names = (entries: readonly LaunchEntry[]): string[] => entries.map(candidate => candidate.target)

test('an exact name comes first, then a name prefix, then a name part, then a target or a command', () => {
  assert.deepEqual(names(rankLaunchEntries(ENTRIES, ' idea ')), [
    '//build:idea',
    '//build:idea_air',
    '//build:idea_community',
    '@community//build:idea_community',
    '//build:my_idea',
  ])
  assert.deepEqual(names(rankLaunchEntries(ENTRIES, '@community')), ['@community//build:idea_community'])
  assert.deepEqual(names(rankLaunchEntries(ENTRIES, 'bazel.cmd run //build:go')), ['//build:goland'])
  assert.deepEqual(rankLaunchEntries(ENTRIES, 'nothing'), [])
  assert.equal(rankLaunchEntries(ENTRIES, '').length, ENTRIES.length)
})

test('the first entry is the best match, else the default name, else the first entry', () => {
  assert.equal(initialLaunchEntry(ENTRIES, 'IDEA (air)', 'IDEA')?.target, '//build:idea_air')
  assert.equal(initialLaunchEntry(ENTRIES, '', 'IDEA')?.target, '//build:idea')
  assert.equal(initialLaunchEntry(ENTRIES, '', 'Gone')?.target, '//build:android_studio')
  assert.equal(initialLaunchEntry(ENTRIES, 'nothing', 'IDEA'), null)
  assert.equal(initialLaunchEntry([], '', 'IDEA'), null)
})
