// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, nothing, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { hostStyles } from "../../foundation/styles"

export class JbFieldGroup extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    label: { type: String, reflect: true },
  }

  static styles = [hostStyles, css`
    fieldset {
      border: 0;
      margin: 0;
      padding: 0;
    }

    legend {
      color: var(--jb-text-color);
      font-weight: var(--jb-font-weight-medium);
      margin-bottom: var(--jb-space-sm);
      padding: 0;
      -webkit-user-select: none;
      user-select: none;
    }

    .body {
      display: grid;
      gap: var(--jb-space-sm);
      -webkit-user-select: text;
      user-select: text;
    }
  `]

  disabled = false
  label = ""

  render(): TemplateResult {
    return html`<fieldset part="group" ?disabled=${this.disabled}>${this.label ? html`<legend part="label">${this.label}</legend>` : nothing}<div part="body" class="body"><slot></slot></div></fieldset>`
  }
}

export function defineJbFieldGroup(registry?: CustomElementRegistryLike): void {
  defineControl("jb-field-group", JbFieldGroup, registry)
}
