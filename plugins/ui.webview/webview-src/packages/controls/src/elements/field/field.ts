// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { hostStyles } from "../../foundation/styles"

export class JbField extends LitElement {
  static properties = {
    error: { type: String, reflect: true },
    help: { type: String, reflect: true },
    label: { type: String, reflect: true },
    required: { type: Boolean, reflect: true },
    warning: { type: String, reflect: true },
  }

  static styles = [hostStyles, css`
    .field {
      display: grid;
      gap: var(--jb-space-xs);
    }

    .body {
      display: grid;
      gap: var(--jb-space-xs);
      user-select: text;
    }
  `]

  error = ""
  help = ""
  label = ""
  required = false
  warning = ""

  render(): TemplateResult {
    const helpTone = this.error ? "error" : this.warning ? "warning" : "default"
    return html`
      <div part="field" class="field">
        ${this.label ? html`<jb-label part="label" ?required=${this.required}>${this.label}</jb-label>` : html`<slot name="label"></slot>`}
        <div part="control" class="body"><slot></slot></div>
        ${this.error || this.warning || this.help ? html`<jb-help-text part="help" tone=${helpTone}>${this.error || this.warning || this.help}</jb-help-text>` : html`<slot name="help"></slot>`}
      </div>
    `
  }
}

export function defineJbField(registry?: CustomElementRegistryLike): void {
  defineControl("jb-field", JbField, registry)
}
