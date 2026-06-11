// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { hostStyles } from "../../foundation/styles"

export class JbHelpText extends LitElement {
  static properties = {
    tone: { type: String, reflect: true },
  }

  static styles = [hostStyles, css`
    .help {
      color: var(--jb-text-muted);
      line-height: var(--jb-line-height-paragraph);
    }

    .error {
      color: var(--jb-danger-color);
    }

    .warning {
      color: var(--jb-warning-color);
    }
  `]

  tone = "default"

  render(): TemplateResult {
    return html`<div part="help" class=${["help", this.tone].join(" ")}><slot></slot></div>`
  }
}

export function defineJbHelpText(registry?: CustomElementRegistryLike): void {
  defineControl("jb-help-text", JbHelpText, registry)
}
