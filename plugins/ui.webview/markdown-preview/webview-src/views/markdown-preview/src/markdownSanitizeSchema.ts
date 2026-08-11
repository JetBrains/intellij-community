// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { defaultSchema, type Options } from "rehype-sanitize"

const defaultAttributes = defaultSchema.attributes || {}

export const markdownSanitizeSchema: Options = {
  ...defaultSchema,
  tagNames: unique([
    ...(defaultSchema.tagNames || []),
    "abbr",
    "br",
    "col",
    "colgroup",
    "details",
    "kbd",
    "mark",
    "section",
    "summary",
    "sub",
    "sup",
  ]),
  attributes: {
    ...defaultAttributes,
    "*": mergeAttributes("*", [["dataSourcepos"]]),
    a: mergeAttributes("a", [["ariaLabel"], ["dataFootnoteBackref"]]),
    code: mergeAttributes("code", [["className", /^language-./, "frontmatterCode", "no-highlight", "nohighlight"], ["dataFrontmatter", "yaml", "toml"]]),
    details: mergeAttributes("details", [["open"]]),
    h2: mergeAttributes("h2", [["className", "sr-only"]]),
    input: mergeAttributes("input", [["checked"], ["disabled"], ["type", "checkbox"]]),
    li: mergeAttributes("li", [["className", "task-list-item"]]),
    section: mergeAttributes("section", [["className", "footnotes"], ["dataFootnotes"]]),
    ul: mergeAttributes("ul", [["className", "contains-task-list"]]),
  } as Options["attributes"],
  protocols: {
    ...defaultSchema.protocols,
    href: mergeProtocols("href", ["file"]),
    src: mergeProtocols("src", ["file"]),
  },
}

function mergeAttributes(tagName: string, additions: Array<unknown>): Array<unknown> {
  return [...((defaultAttributes as Record<string, Array<unknown>>)[tagName] || []), ...additions]
}

function unique<T>(values: Array<T>): Array<T> {
  return Array.from(new Set(values))
}

function mergeProtocols(attributeName: string, additions: Array<string>): Array<string> {
  return unique([...(defaultSchema.protocols?.[attributeName] || []), ...additions])
}
