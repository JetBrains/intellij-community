// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { hostStyles } from "../../foundation/styles"

export class JbSeparator extends LitElement {
  static properties = {
    orientation: { type: String, reflect: true },
  }

  static styles = [hostStyles, css`
    :host {
      display: block;
    }

    .separator {
      background: var(--jb-border-color-muted);
      height: 1px;
      width: 100%;
    }

    :host([orientation="vertical"]) {
      display: inline-block;
      height: 100%;
      min-height: var(--jb-control-height-compact);
    }

    :host([orientation="vertical"]) .separator {
      height: 100%;
      width: 1px;
    }
  `]

  orientation = "horizontal"

  render(): TemplateResult {
    return html`<div part="separator" class="separator" role="separator" aria-orientation=${this.orientation}></div>`
  }
}

export function defineJbSeparator(registry?: CustomElementRegistryLike): void {
  defineControl("jb-separator", JbSeparator, registry)
}
