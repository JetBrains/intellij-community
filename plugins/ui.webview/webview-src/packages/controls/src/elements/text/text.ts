// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { hostStyles } from "../../foundation/styles"

export class JbText extends LitElement {
  static properties = {
    size: { type: String, reflect: true },
    tone: { type: String, reflect: true },
    weight: { type: String, reflect: true },
  }

  static styles = [hostStyles, css`
    .text {
      color: var(--jb-text-color);
      margin: 0;
      user-select: text;
    }

    .small {
      font-size: var(--jb-font-size-small);
    }

    .muted {
      color: var(--jb-text-muted);
    }

    .secondary {
      color: var(--jb-text-secondary);
    }

    .medium {
      font-weight: var(--jb-font-weight-medium);
    }
  `]

  size = "default"
  tone = "default"
  weight = "regular"

  render(): TemplateResult {
    return html`<span part="text" class=${["text", this.size, this.tone, this.weight].join(" ")}><slot></slot></span>`
  }
}

export function defineJbText(registry?: CustomElementRegistryLike): void {
  defineControl("jb-text", JbText, registry)
}
