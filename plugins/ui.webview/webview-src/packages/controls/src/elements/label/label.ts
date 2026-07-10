// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, nothing, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { hostStyles } from "../../foundation/styles"

export class JbLabel extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    for: { type: String, reflect: true },
    required: { type: Boolean, reflect: true },
  }

  static styles = [hostStyles, css`
    label {
      color: var(--jb-text-color);
      display: inline-block;
      -webkit-user-select: none;
      user-select: none;
    }

    :host([disabled]) label {
      color: var(--jb-text-disabled);
    }

    .required {
      color: var(--jb-danger-color);
      margin-left: 2px;
    }
  `]

  disabled = false
  for = ""
  required = false

  render(): TemplateResult {
    return html`<label part="label" for=${this.for || nothing}><slot></slot>${this.required ? html`<span part="required" class="required">*</span>` : nothing}</label>`
  }
}

export function defineJbLabel(registry?: CustomElementRegistryLike): void {
  defineControl("jb-label", JbLabel, registry)
}
