// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, html, type TemplateResult } from "lit"
import { boolAttribute } from "../../foundation/aria"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { buttonStyles, hostStyles } from "../../foundation/styles"

export class JbButton extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    pressed: { type: Boolean, reflect: true },
    selected: { type: Boolean, reflect: true },
    size: { type: String, reflect: true },
    type: { type: String, reflect: true },
    variant: { type: String, reflect: true },
  }

  static styles = [hostStyles, buttonStyles]

  disabled = false
  pressed = false
  selected = false
  size = "default"
  type = "button"
  variant = "default"

  render(): TemplateResult {
    const pressed = this.pressed || this.selected
    return html`
      <button
        part="button"
        class=${this.buttonClass()}
        type=${this.type}
        ?disabled=${this.disabled}
        aria-pressed=${boolAttribute(pressed)}
        data-pressed=${String(this.pressed)}
      >
        <span part="icon" class="icon-slot"><slot name="icon"></slot></span>
        <span part="label"><slot></slot></span>
      </button>
    `
  }

  protected buttonClass(): string {
    return ["button", this.variant, this.size, this.selected ? "selected" : ""].filter(Boolean).join(" ")
  }
}

export function defineJbButton(registry?: CustomElementRegistryLike): void {
  defineControl("jb-button", JbButton, registry)
}
