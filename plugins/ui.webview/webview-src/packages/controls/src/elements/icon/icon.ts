// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, nothing, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { hostStyles } from "../../foundation/styles"

export class JbIcon extends LitElement {
  static properties = {
    label: { type: String, reflect: true },
    name: { type: String, reflect: true },
    size: { type: String, reflect: true },
  }

  static styles = [hostStyles, css`
    .icon {
      align-items: center;
      color: currentColor;
      display: inline-flex;
      height: 16px;
      justify-content: center;
      line-height: 1;
      width: 16px;
    }

    .icon.large {
      height: 20px;
      width: 20px;
    }
  `]

  label = ""
  name = ""
  size = "default"

  render(): TemplateResult {
    return html`<span part="icon" class=${["icon", this.size].join(" ")} role=${this.label ? "img" : nothing} aria-label=${this.label || nothing}><slot>${this.name}</slot></span>`
  }
}

export function defineJbIcon(registry?: CustomElementRegistryLike): void {
  defineControl("jb-icon", JbIcon, registry)
}
