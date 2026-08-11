// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, html, nothing, type TemplateResult } from "lit"
import { boolAttribute } from "../../foundation/aria"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { buttonStyles, hostStyles } from "../../foundation/styles"

export class JbActionButton extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    expanded: { type: Boolean, reflect: true },
    label: { type: String, reflect: true },
    selected: { type: Boolean, reflect: true },
  }

  static styles = [hostStyles, buttonStyles]

  disabled = false
  expanded = false
  label = ""
  selected = false

  render(): TemplateResult {
    return html`
      <button
        part="button"
        class=${["button", "icon", this.selected ? "selected" : ""].filter(Boolean).join(" ")}
        type="button"
        ?disabled=${this.disabled}
        aria-label=${this.label || nothing}
        aria-expanded=${this.expanded ? "true" : nothing}
        aria-pressed=${boolAttribute(this.selected)}
      >
        <span part="icon" class="icon-slot"><slot></slot></span>
      </button>
    `
  }
}

export function defineJbActionButton(registry?: CustomElementRegistryLike): void {
  defineControl("jb-action-button", JbActionButton, registry)
}
