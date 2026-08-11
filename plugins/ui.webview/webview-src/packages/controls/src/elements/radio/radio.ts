// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, html, nothing, type TemplateResult } from "lit"
import { boolAttribute } from "../../foundation/aria"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { emitStandardEvent, emitValueEvent } from "../../foundation/events"
import { choiceStyles, hostStyles } from "../../foundation/styles"

export class JbRadio extends LitElement {
  static properties = {
    checked: { type: Boolean, reflect: true },
    disabled: { type: Boolean, reflect: true },
    name: { type: String, reflect: true },
    readOnly: { type: Boolean, reflect: true, attribute: "readonly" },
    value: { type: String, reflect: true },
  }

  static styles = [hostStyles, choiceStyles]

  checked = false
  disabled = false
  name = ""
  readOnly = false
  value = "on"

  render(): TemplateResult {
    return html`
      <label part="label" class="choice radio">
        <input
          part="input"
          class="native-check"
          type="radio"
          name=${this.name || nothing}
          value=${this.value}
          .checked=${this.checked}
          ?disabled=${this.disabled}
          aria-readonly=${boolAttribute(this.readOnly)}
          @click=${this.onReadOnlyClick}
          @change=${this.onNativeChange}
        >
        <span part="control" class="mark"></span>
        <span part="text" class="choice-label"><slot></slot></span>
      </label>
    `
  }

  private onReadOnlyClick(event: Event): void {
    if (this.readOnly) {
      event.preventDefault()
    }
  }

  private onNativeChange(event: Event): void {
    event.stopPropagation()
    const input = event.currentTarget as HTMLInputElement
    if (this.readOnly) {
      input.checked = this.checked
      return
    }
    this.checked = input.checked
    if (this.checked) {
      emitStandardEvent(this, "input")
      emitStandardEvent(this, "change")
      emitValueEvent(this, "jb-change", this.value)
    }
  }
}

export function defineJbRadio(registry?: CustomElementRegistryLike): void {
  defineControl("jb-radio", JbRadio, registry)
}
