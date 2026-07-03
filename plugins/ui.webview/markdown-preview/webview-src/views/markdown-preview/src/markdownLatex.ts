// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import renderMathInElement from "katex/contrib/auto-render"

const latexDelimiters = [
  { left: "$$", right: "$$", display: true },
  { left: "\\[", right: "\\]", display: true },
  { left: "\\(", right: "\\)", display: false },
  { left: "$", right: "$", display: false },
  { left: "\\begin{equation}", right: "\\end{equation}", display: true },
  { left: "\\begin{align}", right: "\\end{align}", display: true },
  { left: "\\begin{alignat}", right: "\\end{alignat}", display: true },
  { left: "\\begin{gather}", right: "\\end{gather}", display: true },
  { left: "\\begin{CD}", right: "\\end{CD}", display: true },
]

export function renderMarkdownLatex(): void {
  const contentElement = document.getElementById("content")
  if (!contentElement) return

  renderMathInElement(contentElement, {
    delimiters: latexDelimiters,
    ignoredClasses: ["katex"],
    throwOnError: false,
  })
}
