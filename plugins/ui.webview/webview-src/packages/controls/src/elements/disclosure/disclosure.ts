// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, nothing, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { buttonStyles, hostStyles } from "../../foundation/styles"

export class JbDisclosure extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    label: { type: String, reflect: true },
    open: { type: Boolean, reflect: true },
  }

  static styles = [hostStyles, buttonStyles, css`
    .content {
      margin-top: var(--jb-space-sm);
      padding-left: calc(var(--jb-control-height-compact) + var(--jb-space-xs));
      user-select: text;
    }
  `]

  disabled = false
  label = ""
  open = false

  render(): TemplateResult {
    return html`
      <button part="summary" class="button link" type="button" ?disabled=${this.disabled} aria-expanded=${String(this.open)} @click=${() => this.open = !this.open}>
        <span part="chevron" class=${["chevron", this.open ? "" : "right"].filter(Boolean).join(" ")} aria-hidden="true"></span>
        <span part="label"><slot name="summary">${this.label}</slot></span>
      </button>
      ${this.open ? html`<div part="content" class="content"><slot></slot></div>` : nothing}
    `
  }
}

export function defineJbDisclosure(registry?: CustomElementRegistryLike): void {
  defineControl("jb-disclosure", JbDisclosure, registry)
}
