// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, nothing, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { buttonStyles, hostStyles, popupStyles } from "../../foundation/styles"

export class JbContextHelp extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    open: { type: Boolean, reflect: true },
    text: { type: String, reflect: true },
  }

  static styles = [hostStyles, buttonStyles, popupStyles, css`
    .help {
      border-radius: 50%;
      font-weight: var(--jb-font-weight-medium);
      height: 18px;
      min-height: 18px;
      min-width: 18px;
      padding: 0;
      width: 18px;
    }

    .popup {
      line-height: var(--jb-line-height-paragraph);
      max-width: 260px;
      white-space: normal;
    }
  `]

  disabled = false
  open = false
  text = ""

  render(): TemplateResult {
    return html`
      <span part="root" class="menu-root">
        <button part="button" class="button toolbar help" type="button" ?disabled=${this.disabled} aria-label="Context help" aria-expanded=${String(this.open)} @click=${() => this.open = !this.open}>?</button>
        ${this.open ? html`<div part="popup" class="popup"><slot>${this.text}</slot></div>` : nothing}
      </span>
    `
  }
}

export function defineJbContextHelp(registry?: CustomElementRegistryLike): void {
  defineControl("jb-context-help", JbContextHelp, registry)
}
