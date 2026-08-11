// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, html, type TemplateResult } from "lit"
import { boolAttribute } from "../../foundation/aria"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { buttonStyles, hostStyles } from "../../foundation/styles"

export class JbButton extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    hasIcon: { state: true },
    pressed: { type: Boolean, reflect: true },
    selected: { type: Boolean, reflect: true },
    size: { type: String, reflect: true },
    type: { type: String, reflect: true },
    variant: { type: String, reflect: true },
  }

  static styles = [hostStyles, buttonStyles]

  disabled = false
  private hasIcon = false
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
        <span part="icon" class=${this.hasIcon ? "icon-slot" : "icon-slot empty"}>
          <slot name="icon" @slotchange=${this.updateIconState}></slot>
        </span>
        <span part="label"><slot></slot></span>
      </button>
    `
  }

  protected buttonClass(): string {
    return ["button", this.variant, this.size, this.selected ? "selected" : ""].filter(Boolean).join(" ")
  }

  private updateIconState(event: Event): void {
    const slot = event.target as HTMLSlotElement
    const hasAssignedElement = slot.assignedElements({ flatten: true }).length > 0
    const hasAssignedText = slot.assignedNodes({ flatten: true }).some(node => Boolean(node.textContent?.trim()))
    this.hasIcon = hasAssignedElement || hasAssignedText
  }
}

export function defineJbButton(registry?: CustomElementRegistryLike): void {
  defineControl("jb-button", JbButton, registry)
}
