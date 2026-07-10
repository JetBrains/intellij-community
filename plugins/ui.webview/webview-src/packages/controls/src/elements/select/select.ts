// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, html, nothing, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { emitStandardEvent, emitValueEvent } from "../../foundation/events"
import { WebViewFocusLeaveController } from "../../foundation/focus"
import { normalizeOptions } from "../../foundation/options"
import { hostStyles, inputStyles } from "../../foundation/styles"
import type { JbControlOption } from "../../foundation/types"

export class JbSelect extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    invalid: { type: Boolean, reflect: true },
    items: { attribute: false },
    name: { type: String, reflect: true },
    placeholder: { type: String, reflect: true },
    required: { type: Boolean, reflect: true },
    value: { type: String, reflect: true },
  }

  static styles = [hostStyles, inputStyles]

  disabled = false
  invalid = false
  items: JbControlOption[] = []
  name = ""
  placeholder = ""
  required = false
  value = ""

  constructor() {
    super()
    new WebViewFocusLeaveController(this, () => this.renderRoot.querySelector<HTMLSelectElement>("select")?.blur())
  }

  render(): TemplateResult {
    const options = normalizeOptions(this.items)
    return html`
      <span part="control" class="select-wrap">
        <select
          part="select"
          class="select"
          name=${this.name || nothing}
          .value=${this.value}
          ?disabled=${this.disabled}
          ?required=${this.required}
          aria-invalid=${this.invalid ? "true" : "false"}
          @input=${this.onSelectInput}
          @change=${this.onSelectChange}
        >
          ${this.placeholder ? html`<option value="" disabled ?selected=${this.value === ""}>${this.placeholder}</option>` : nothing}
          ${options.map(option => html`<option value=${option.value} ?disabled=${Boolean(option.disabled)}>${option.label}</option>`)}
        </select>
      </span>
    `
  }

  private onSelectInput(event: Event): void {
    event.stopPropagation()
    this.value = (event.currentTarget as HTMLSelectElement).value
    emitStandardEvent(this, "input")
  }

  private onSelectChange(event: Event): void {
    event.stopPropagation()
    this.value = (event.currentTarget as HTMLSelectElement).value
    emitStandardEvent(this, "change")
    emitValueEvent(this, "jb-change", this.value)
  }
}

export function defineJbSelect(registry?: CustomElementRegistryLike): void {
  defineControl("jb-select", JbSelect, registry)
}
