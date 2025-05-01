// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("CssUnresolvedCustomProperty", "CssUnusedSymbol")

package org.intellij.plugins.markdown.settings

import org.intellij.lang.annotations.Language

class MarkdownGitHubStyles {
  companion object {
    @Language("CSS")
    const val GITHUB_LIGHT_CSS = """
:root {
  --github-md-bg-color: white;
  --github-md-border-color: #D1D9E0;
  --github-md-border-color-muted: rgba(209, 217, 224, 0.7);
  --github-md-fg-color: #1F2328;
  --github-md-fg-color-attention: #9A6700;
  --github-md-fg-color-muted: #59636E;
  --github-md-fg-color-success: #1A7F37;
  --github-md-code-background-color: #F6F8FA;
  --github-md-link-color: #0969DA;
  --github-md-neutral-muted: #818B981F;
  --github-md-octicon-fg: black;
  --github-md-scrollbar-track: #EDEDED;
  --github-md-scrollbar-track-bg: #F6F8FA;
  --github-md-scrollbar-thumb-bg: #959595;
  --github-md-scrollbar-thumb-border: #F6F6F6;
  --github-md-syntax-comment: #59636E;
  --github-md-syntax-constant: #0550AE;
  --github-md-syntax-entity: #6639BA;
  --github-md-syntax-entity-tag: #0550AE;
  --github-md-syntax-keyword: #CF222E;
  --github-md-syntax-markup-deleted: #82071E;
  --github-md-syntax-markup-deleted-bg: #FFEBE9;
  --github-md-syntax-markup-inserted: #116329;
  --github-md-syntax-markup-inserted-bg: #DAFBE1;
  --github-md-syntax-storage-modifier: #1F2328;
  --github-md-syntax-string: #0A3069;
  --github-md-syntax-string-regex: #116329;
  --github-md-syntax-variable: #953800;
  --github-md-table-border-color: #808080;
}

"""

    @Language("CSS")
    const val GITHUB_DARK_CSS = """
:root {
  --github-md-bg-color: #0D1117;
  --github-md-border-color: #3D444D;
  --github-md-border-color-muted: rgba(61, 68, 77, 0.7);
  --github-md-fg-color: #F0F6FC;
  --github-md-fg-color-attention: #9A6700;
  --github-md-fg-color-muted: #9198A1;
  --github-md-fg-color-success: #1A7F37;
  --github-md-code-background-color: #151B23;
  --github-md-link-color: #4493F8;
  --github-md-neutral-muted: rgba(101, 108, 118, 0.2);
  --github-md-octicon-fg: white;
  --github-md-scrollbar-track: #121212;
  --github-md-scrollbar-track-bg: #050505;
  --github-md-scrollbar-thumb-bg: #6B6B6B;
  --github-md-scrollbar-thumb-border: #0A0A0A;
  --github-md-syntax-comment: #9198A1;
  --github-md-syntax-constant: #79C0FF;
  --github-md-syntax-entity: #D2A8FF;
  --github-md-syntax-entity-tag: #7EE787;
  --github-md-syntax-keyword: #FF7B72;
  --github-md-syntax-markup-deleted: #FFDCD7;
  --github-md-syntax-markup-deleted-bg: #67060C;
  --github-md-syntax-markup-inserted: #AFF5B4;
  --github-md-syntax-markup-inserted-bg: #033A16;
  --github-md-syntax-storage-modifier: #F0F6FC;
  --github-md-syntax-string: #A5D6FF;
  --github-md-syntax-string-regex: #7EE787;
  --github-md-syntax-variable: #FFA657;
  --github-md-table-border-color: gray;
}

"""

    @Language("CSS")
    const val GITHUB_COMMON_CSS = """
:root {
  --github-md-body-font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans", Helvetica, Arial, "Apple Color Emoji", "Segoe UI Emoji";
  --github-md-mono-font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, "Liberation Mono";
}

@font-face {
  font-family: octicons-anchor;
  src: url(data:font/woff;charset=utf-8;base64,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) format('woff');
}

blockquote, body, dd, dl, dt, li, p, table, td, th, ul {
  color: var(--github-md-fg-color) !important;
  font-family: var(--github-md-body-font-family), sans-serif !important;
}

html {
  font-size: var(--default-font-size) !important;
}

body {
  background-color: var(--github-md-bg-color);
  line-height: 1.5;
  word-wrap: break-word;
}

body * {
  box-sizing: border-box;
}

body > div {
  margin: 0 auto;
  max-width: 51.875rem;
}

.markdown-body > *:first-child {
  margin-top: 0 !important;
}

.markdown-body > *:last-child {
  margin-bottom: 0 !important;
}

a {
  background-color: transparent;
  color: var(--github-md-link-color);
  text-decoration: underline;
  text-underline-offset: 0.2em;
}

a:active,
a:hover {
  outline: 0;
}

strong {
  font-weight: 600 !important;
}

img {
  border: 0;
}

hr {
  background: var(--github-md-border-color);
  border: 0;
  height: 0.25em;
  margin: 1.5rem 0;
  overflow: hidden;
  padding: 0;
}

hr:before {
  display: table;
  content: "";
}

hr:after {
  display: table;
  clear: both;
  content: "";
}

code, kbd, pre {
  font-family: var(--github-md-mono-font-family), monospace;
  color: inherit;
  background-color: var(--github-md-code-background-color);
}

kbd {
  background-color: var(--github-md-code-background-color);
  border: solid 1px var(--github-md-border-color-muted);
  border-radius: 0.545em;
  box-shadow: inset 0 -1px 0 var(--github-md-border-color-muted);
  display: inline-block;
  font-size: 0.6875em;
  line-height: 0.909em;
  padding: 0.364em;
  vertical-align: middle;
}

table {
  border-collapse: collapse;
  border-spacing: 0;
}

th {
  font-weight: 600 !important;
  text-align: match-parent;
}

td, th {
  padding: 0;
}

table tr:nth-child(2n) {
  background-color: var(--github-md-code-background-color);
}

table th {
  font-weight: bold;
}

table th,
table td {
  padding: 0.375em 0.8125em;
  border: 1px solid var(--github-md-border-color);
}

table tr {
  background-color: var(--github-md-bg-color);
  border-color: var(--github-md-table-border-color);
  border-top: 1px solid var(--github-md-border-color-muted);
}

h1, h2, h3, h4, h5 h6 {
  font-family: var(--github-md-body-font-family), sans-serif !important;
  font-weight: 600 !important;
  line-height: 1.25;
  margin-bottom: 1rem;
  margin-top: 1.5rem;
  padding-top: 0 !important;
}

h1 tt, h1 code,
h2 tt, h2 code,
h3 tt, h3 code,
h4 tt, h4 code,
h5 tt, h5 code,
h6 tt, h6 code {
  padding: 0 0.2em !important;
  font-size: inherit !important;
}

h1 {
  border-bottom: 1px solid var(--github-md-border-color-muted) !important;
  font-size: 2rem !important;
  margin: 0.67rem 0 !important;
  padding-bottom: 0.3em;
}

h2 {
  border-bottom: 1px solid var(--github-md-border-color-muted);
  font-size: 1.5rem !important;
  padding-bottom: 0.3em;
}

h3 {
  font-size: 1.25rem !important;
}

h4 {
  font-size: 1rem !important;
}

h5 {
  font-size: 0.875rem !important;
}

h6 {
  font-size: 0.85rem !important;
}

ul, ol {
  padding: 0;
  margin-top: 0;
  margin-bottom: 0;
}

li + li {
  margin-top: 0.25em;
}

ol ol,
ul ol {
  list-style-type: lower-roman;
}

ul ul ol,
ul ol ol,
ol ul ol,
ol ol ol {
  list-style-type: lower-alpha;
}

dd {
  margin-left: 0;
}

pre {
  font-size: 0.75em;
  margin-bottom: 0;
  margin-top: 0;
  overflow: auto;
  word-wrap: normal;
}

.octicon {
  font: normal normal normal 16px/1 octicons-anchor;
  display: inline-block;
  text-decoration: none;
  text-rendering: auto;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
  user-select: none;
}

.octicon-link:before {
  content: '\f05c';
}

.anchor {
  position: absolute;
  top: 0;
  left: 0;
  display: block;
  padding-right: 0.375em;
  padding-left: 1.875em;
  margin-left: -1.875em;
}

.anchor:focus {
  outline: none;
}

h1 .octicon-link,
h2 .octicon-link,
h3 .octicon-link,
h4 .octicon-link,
h5 .octicon-link,
h6 .octicon-link {
  display: none;
  color: var(--github-md-octicon-fg);
  vertical-align: middle;
}

h1:hover .anchor,
h2:hover .anchor,
h3:hover .anchor,
h4:hover .anchor,
h5:hover .anchor,
h6:hover .anchor {
  padding-left: 0.5em;
  margin-left: -1.875em;
  text-decoration: none;
}

h1:hover .anchor .octicon-link,
h2:hover .anchor .octicon-link,
h3:hover .anchor .octicon-link,
h4:hover .anchor .octicon-link,
h5:hover .anchor .octicon-link,
h6:hover .anchor .octicon-link {
  display: inline-block;
}

h1 .anchor {
  line-height: 1;
}

h2 .anchor {
  line-height: 1;
}

h3 {
  font-size: 1.5em;
  line-height: 1.43;
}

h3 .anchor {
  line-height: 1.2;
}

h4 {
  font-size: 1.25em;
}

h4 .anchor {
  line-height: 1.2;
}

h5 {
  font-size: 1em;
}

h5 .anchor {
  line-height: 1.1;
}

h6 {
  font-size: 1em;
  color: var(--github-md-fg-color-muted);
}

h6 .anchor {
  line-height: 1.1;
}

p, blockquote, ul, ol, dl, table, pre {
  margin-top: 1em;
  margin-bottom: 1em;
}

ul,
ol {
  padding-left: 2em;
}

ul ul,
ul ol,
ol ol,
ol ul {
  margin-top: 0;
  margin-bottom: 0;
}

dl {
  padding: 0;
}

dl dt {
  padding: 0;
  margin-top: 1em;
  font-size: 1em;
  font-style: italic;
  font-weight: bold;
}

dl dd {
  padding: 0 1em;
  margin-bottom: 1em;
}

blockquote {
  background-color: transparent !important;
  border-left: 0.25em solid var(--github-md-border-color);
  color: var(--github-md-fg-color-muted) !important;
  margin: 0;
  padding: 0 0.9375em;
}

blockquote p {
  color: var(--github-md-fg-color-muted) !important;
}

blockquote > :first-child {
  margin-top: 0;
}

blockquote > :last-child {
  margin-bottom: 0;
}

img {
  max-width: 100%;
}

code {
  background-color: var(--github-md-neutral-muted);
  border-radius: 0.375em;
  color: inherit;
  font-family: var(--github-md-mono-font-family), monospace;
  font-size: 85%;
  font-weight: inherit;
  margin: 0;
  padding: 0.2em 0.4em;
  white-space: break-spaces;
}

pre code {
  background-color: transparent;
  border: 0;
  display: inline;
  font-size: 0.85rem;
  line-height: inherit;
  margin: 0;
  max-width: initial;
  overflow: initial;
  padding: 0;
  white-space: pre;
  word-wrap: normal;
}

pre code:before,
pre code:after {
  content: normal;
}

.highlight {
  margin-bottom: 1em;
}

.highlight pre, pre {
  padding: 1.33em;
  overflow: auto;
  line-height: 1.45;
  background-color: var(--github-md-code-background-color) !important;
  border-radius: 0.1875em;
}

.task-list-item {
  list-style-type: none;
}

.task-list-item + .task-list-item {
  margin-top: 0.1875em;
}

.task-list-item input {
  margin: 0 0.35em 0.25em -0.6em;
  vertical-align: middle;
}

span.user-del {
  text-decoration: line-through;
}

::-webkit-scrollbar {
  width: 0.75em;
  height: 0.75rem;
  background-color: inherit;
}

::-webkit-scrollbar-thumb {
  background-color: var(--github-md-scrollbar-thumb-bg);
  border: 2px solid var(--github-md-scrollbar-thumb-border);
  -webkit-border-radius: 0.625rem;
}

::-webkit-scrollbar-track {
  background-color: var(--github-md-scrollbar-track-bg);
}

::-webkit-scrollbar-track:vertical {
  -webkit-box-shadow: -1px 0 0 var(--github-md-scrollbar-track);
}

::-webkit-scrollbar-track:horizontal {
  -webkit-box-shadow: 0 -1px 0 var(--github-md-scrollbar-track);
}

"""

/*
  Class names used on GitHub for syntax coloring

  pl-c:      --github-md-syntax-comment
  pl-c1      --github-md-syntax-constant
  pl-e:      --github-md-syntax-entity
  pl-en:     --github-md-syntax-entity
  pl-ent:    --github-md-syntax-entity-tag
  pl-k:      --github-md-syntax-keyword
  pl-s1:     --github-md-fg-color
  pl-s:      --github-md-syntax-string
  pl-sr:     --github-md-syntax-string-regex
  pl-smi:    --github-md-syntax-storage-modifier
  pl-v:      --github-md-syntax-variable
*/

    @Language("CSS")
    const val GITHUB_SYNTAX_COLOR_CSS_CLASSES = """
.hljs-doctag,
.hljs-keyword,
.hljs-meta .hljs-keyword,
.hljs-template-tag,
.hljs-template-variable,
.hljs-type {
  color: var(--github-md-syntax-keyword);
}

.hljs-title,
.hljs-title.class_,
.hljs-title.class_.inherited__,
.hljs-title.function_ {
  color: var(--github-md-syntax-entity);
}

.hljs-attribute,
.hljs-selector-attr,
.hljs-selector-class,
.hljs-selector-id,
.hljs-variable {
  color: var(--github-md-syntax-variable);
}

.hljs-attr,
.hljs-literal,
.hljs-meta,
.hljs-number,
.hljs-variable.constant_ {
  color: var(--github-md-syntax-constant);
}

.hljs-meta .hljs-string,
.hljs-regexp,
.hljs-string {
  color: var(--github-md-syntax-string);
}

.hljs-built_in,
.hljs-symbol {
  color: var(--ithub-md-syntax-variable);
}

.hljs-code,
.hljs-comment,
.hljs-formula {
  color: var(--github-md-syntax-comment);
}

.hljs-name,
.hljs-quote,
.hljs-selector-pseudo,
.hljs-selector-tag {
  color: var(--github-md-fg-color-success);
}

.hljs-operator,
.hljs-subst,
.hljs-variable.language_  {
  color: var(--github-md-fg-color);
}

.hljs-section {
  color: var(--github-md-syntax-constant);
  font-weight: 700;
}

.hljs-bullet {
  color: var(--github-md-fg-color-attention);
}

.hljs-emphasis {
  color: var(--github-md-fg-color);
  font-style: italic
}

.hljs-strong {
  color: var(--github-md-fg-color);
  font-weight: 700;
}

.hljs-addition {
  color: var(--github-md-syntax-markup-inserted);
  background-color: var(--github-md-syntax-markup-inserted-bg);
}

.hljs-deletion {
  color: var(--github-md-syntax-markup-deleted);
  background-color: var(--github-md-syntax-markup-deleted-bg);
}

/* CSS tweaks */

.language-css .hljs-attribute,
.language-css .hljs-selector-attr,
.language-css .hljs-selector-class,
.language-css .hljs-selector-id,
.language-css .hljs-selector-pseudo,
.language-css .hljs-selector-tag {
  color: var(--github-md-syntax-constant);
}

.language-css .hljs-built_in {
  color: var(--github-md-syntax-entity);
}

.language-css .hljs-meta {
  color: var(--github-md-syntax-keyword);
}

/* HTML tweaks */

.language-html .hljs-keyword {
  color: var(--github-md-syntax-constant);
}

.language-html .hljs-name {
  color: var(--github-md-syntax-entity-tag);
}

/* JAVA tweaks */

.language-java .hljs-comment * {
  color: var(--github-md-syntax-comment);
}

.language-java .hljs-title.class_,
.language-java .hljs-type,
.language-java .hljs-variable {
  color: var(--github-md-fg-color);
}

/* JavaScript tweaks */

.language-javascript .hljs-name,
.language-html .language-javascript .hljs-name,
.language-javascript .hljs-title.class_ {
  color: var(--github-md-syntax-variable);
}

.language-html .language-javascript .hljs-keyword {
  color: var(--github-md-syntax-keyword);
}

/* JSON/JSON5 tweaks */

.language-json .hljs-keyword,
.language-json5 .hljs-keyword {
  color: var(--github-md-syntax-constant);
}

.language-json .hljs-attr,
.language-json5 .hljs-attr {
  color: var(--github-md-syntax-entity-tag);
}

/* Kotlin tweaks */

.language-kotlin .hljs-built_in {
  color: var(--github-md-syntax-constant);
}

.language-kotlin .hljs-operator {
  color: var(--github-md-syntax-keyword);
}

.language-kotlin .hljs-meta {
  color: var(--github-md-fg-color);
}

/* PHP tweaks */

.language-php .hljs-comment * {
  color: var(--github-md-syntax-comment);
}

.language-php .hljs-meta {
  color: var(--github-md-syntax-entity-tag);
}

.language-php .hljs-title,
.language-php .hljs-variable.constant_ {
  color: var(--github-md-fg-color);
}

.language-php .hljs-title.class_ {
  color: var(--github-md-syntax-variable);
}

.language-php .hljs-title.function_ {
  color: var(--github-md-syntax-entity);
}

.language-php .hljs-variable {
  color: var(--github-md-syntax-constant);
}

/* Python tweaks */

.language-python .hljs-built_in {
  color: var(--github-md-syntax-entity);
}

.language-python .hljs-meta,
.language-python .hljs-params,
.language-python .hljs-title.function_ {
  color: var(--github-md-syntax-entity);
}

.language-python .hljs-title.class_,
.language-python .hljs-type {
  color: var(--github-md-syntax-variable);
}

/* SQL tweaks */

.language-sql .hljs-operator {
  color: var(--github-md-syntax-keyword);
}

/* TypeScript tweaks */

.language-typescript .hljs-attr,
.language-typescript .hljs-title.class_ {
  color: var(--github-md-fg-color);
}

.language-typescript .hljs-meta {
  color: var(--github-md-syntax-entity);
}

.language-typescript .hljs-comment .hljs-type,
.language-typescript .hljs-comment .hljs-variable {
  color: var(--github-md-syntax-comment);
}

/* XML tweaks */

.language-xml .hljs-attr,
.language-xml .hljs-keyword {
  color: var(--github-md-syntax-entity);
}

.language-xml .hljs-name {
  color: var(--github-md-syntax-entity-tag);
}
"""
  }
}
