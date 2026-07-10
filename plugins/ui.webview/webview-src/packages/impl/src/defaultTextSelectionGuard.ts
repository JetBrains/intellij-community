// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

const DEFAULT_TEXT_SELECTION_GUARD_META_NAME = "wvi-enable-default-text-selection-guard"
const DEFAULT_TEXT_SELECTION_GUARD_STYLE_ATTRIBUTE = "data-wvi-default-text-selection-guard"

export const DEFAULT_TEXT_SELECTION_GUARD_CSS = `
:where(body.ij-webview-root) {
  -webkit-user-select: none;
  user-select: none;
  cursor: default;
}

:where(body.ij-webview-root) :where(
  input,
  textarea,
  [contenteditable]:not([contenteditable="false"]),
  .webview-selectable-text,
  .webview-selectable-text *,
  [data-webview-selectable="true"],
  [data-webview-selectable="true"] *
) {
  -webkit-user-select: text;
  user-select: text;
}
`.trim()

export function installWebViewDefaultTextSelectionGuard(): void {
  if (!isDefaultTextSelectionGuardEnabled() || document.head.querySelector(`style[${DEFAULT_TEXT_SELECTION_GUARD_STYLE_ATTRIBUTE}]`) != null) {
    return
  }

  const style = document.createElement("style")
  style.setAttribute(DEFAULT_TEXT_SELECTION_GUARD_STYLE_ATTRIBUTE, "")
  style.textContent = DEFAULT_TEXT_SELECTION_GUARD_CSS
  document.head.appendChild(style)
}

function isDefaultTextSelectionGuardEnabled(): boolean {
  const meta = document.head.querySelector(`meta[name="${DEFAULT_TEXT_SELECTION_GUARD_META_NAME}"]`)
  return meta?.getAttribute("content") !== "false"
}
