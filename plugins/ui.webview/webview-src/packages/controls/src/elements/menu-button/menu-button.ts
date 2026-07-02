// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, html, nothing, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { emitStandardEvent, emitValueEvent } from "../../foundation/events"
import { WebViewFocusLeaveController } from "../../foundation/focus"
import { normalizeOptions, optionLabel } from "../../foundation/options"
import { buttonStyles, hostStyles, popupStyles } from "../../foundation/styles"
import type { JbControlOption } from "../../foundation/types"

export class JbMenuButton extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    items: { attribute: false },
    label: { type: String, reflect: true },
    open: { type: Boolean, reflect: true },
    value: { type: String, reflect: true },
    variant: { type: String, reflect: true },
  }

  static styles = [hostStyles, buttonStyles, popupStyles]

  disabled = false
  items: JbControlOption[] = []
  label = ""
  open = false
  value = ""
  variant = "default"

  constructor() {
    super()
    new WebViewFocusLeaveController(this, () => {
      this.open = false
    })
  }

  render(): TemplateResult {
    const options = normalizeOptions(this.items)
    return html`
      <span part="root" class="menu-root">
        <button part="button" class=${["button", this.variant].filter(Boolean).join(" ")} type="button" ?disabled=${this.disabled} aria-haspopup="menu" aria-expanded=${String(this.open)} @click=${this.toggleOpen} @keydown=${this.onButtonKeyDown}>
          <span part="label"><slot>${this.label || optionLabel(options, this.value)}</slot></span>
          <span part="chevron" class="chevron">v</span>
        </button>
        ${this.open ? html`<div part="menu" class="popup" role="menu">${options.length > 0 ? options.map(option => this.renderMenuItem(option)) : html`<slot name="menu"></slot>`}</div>` : nothing}
      </span>
    `
  }

  protected renderMenuItem(option: JbControlOption): TemplateResult {
    return html`<button part="menu-item" class="menu-item" type="button" role="menuitem" ?disabled=${Boolean(option.disabled)} @click=${() => this.selectOption(option)}>${option.label}</button>`
  }

  private toggleOpen(): void {
    if (!this.disabled) {
      this.open = !this.open
    }
  }

  private onButtonKeyDown(event: KeyboardEvent): void {
    if (event.key === "Escape") {
      this.open = false
    }
    else if (event.key === "ArrowDown" || event.key === "Enter" || event.key === " ") {
      event.preventDefault()
      this.open = true
    }
  }

  private selectOption(option: JbControlOption): void {
    if (this.disabled || option.disabled) {
      return
    }
    this.value = option.value
    this.open = false
    emitStandardEvent(this, "change")
    emitValueEvent(this, "jb-select", this.value)
  }
}

export function defineJbMenuButton(registry?: CustomElementRegistryLike): void {
  defineControl("jb-menu-button", JbMenuButton, registry)
}
