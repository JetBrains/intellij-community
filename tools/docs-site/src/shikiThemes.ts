// The editor schemes of the Islands themes as shiki TextMate themes, used as a dual light/dark pair by the
// source viewer. Islands Light uses the Light scheme (community/platform/platform-resources/src/themes/expUI/
// expUI_lightScheme.xml); Islands Dark uses islands/IslandSchemeDark.xml over Darcula, with the editor background
// of the island (#191a1c, editor-bg of ManyIslandsDark.theme.json). Scopes without an entry
// inherit the default foreground, matching IntelliJ (function calls, class names, parameters and
// punctuation are plain text in both schemes).

import type { ThemeRegistrationAny } from 'shiki/core'

interface IntellijPalette {
  text: string
  background: string
  keyword: string
  string: string
  number: string
  comment: string
  docComment: string
  functionDeclaration: string
  metadata: string
  field: string
  constant: string
  commentFontStyle?: 'italic'
}

function intellijTheme(name: string, type: 'light' | 'dark', p: IntellijPalette): ThemeRegistrationAny {
  return {
    name,
    type,
    fg: p.text,
    bg: p.background,
    settings: [
      { settings: { foreground: p.text, background: p.background } },
      {
        scope: ['keyword', 'storage', 'constant.language', 'variable.language', 'entity.name.tag'],
        settings: { foreground: p.keyword },
      },
      { scope: ['string', 'punctuation.definition.string'], settings: { foreground: p.string } },
      { scope: ['constant.numeric'], settings: { foreground: p.number } },
      { scope: ['comment'], settings: { foreground: p.comment, fontStyle: p.commentFontStyle } },
      { scope: ['comment.block.documentation'], settings: { foreground: p.docComment, fontStyle: 'italic' } },
      { scope: ['entity.name.function'], settings: { foreground: p.functionDeclaration } },
      {
        scope: [
          'storage.type.annotation',
          'meta.annotation',
          'punctuation.definition.annotation',
          'entity.name.type.annotation',
          'entity.other.attribute-name',
        ],
        settings: { foreground: p.metadata },
      },
      {
        scope: ['variable.other.property', 'variable.other.member', 'variable.other.object.property'],
        settings: { foreground: p.field },
      },
      { scope: ['variable.other.constant'], settings: { foreground: p.constant, fontStyle: 'italic' } },
      { scope: ['markup.bold'], settings: { fontStyle: 'bold' } },
      { scope: ['markup.italic'], settings: { fontStyle: 'italic' } },
    ],
  }
}

export const intellijLight = intellijTheme('intellij-light', 'light', {
  text: '#080808',
  background: '#ffffff',
  keyword: '#0033b3',
  string: '#067d17',
  number: '#1750eb',
  comment: '#8c8c8c',
  docComment: '#8c8c8c',
  functionDeclaration: '#00627a',
  metadata: '#9e880d',
  field: '#871094',
  constant: '#871094',
  commentFontStyle: 'italic',
})

export const intellijDark = intellijTheme('intellij-dark', 'dark', {
  text: '#bcbec4',
  background: '#191a1c',
  keyword: '#cf8e6d',
  string: '#6aab73',
  number: '#2aacb8',
  comment: '#7a7e85',
  docComment: '#5f826b',
  functionDeclaration: '#56a8f5',
  metadata: '#b3ae60',
  field: '#c77dbb',
  constant: '#c77dbb',
})
