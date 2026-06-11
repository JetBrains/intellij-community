// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, html, nothing, type TemplateResult } from "lit"
import { emitStandardEvent, emitValueEvent } from "../../foundation/events"
import { hostStyles, inputStyles } from "../../foundation/styles"

export class TextInputBase extends LitElement {
  static properties = {
    autocomplete: { type: String, reflect: true },
    disabled: { type: Boolean, reflect: true },
    inputMode: { type: String, reflect: true, attribute: "inputmode" },
    invalid: { type: Boolean, reflect: true },
    maxLength: { type: Number, reflect: true, attribute: "maxlength" },
    name: { type: String, reflect: true },
    placeholder: { type: String, reflect: true },
    readOnly: { type: Boolean, reflect: true, attribute: "readonly" },
    required: { type: Boolean, reflect: true },
    value: { type: String, reflect: true },
  }

  static styles = [hostStyles, inputStyles]

  autocomplete = ""
  disabled = false
  inputMode = ""
  invalid = false
  maxLength = -1
  name = ""
  placeholder = ""
  readOnly = false
  required = false
  value = ""

  protected get inputType(): string {
    return "text"
  }

  render(): TemplateResult {
    return html`
      <input
        part="input"
        class="field-control"
        type=${this.inputType}
        name=${this.name || nothing}
        autocomplete=${this.autocomplete || nothing}
        inputmode=${this.inputMode || nothing}
        maxlength=${this.maxLength >= 0 ? this.maxLength : nothing}
        placeholder=${this.placeholder || nothing}
        .value=${this.value}
        ?disabled=${this.disabled}
        ?readonly=${this.readOnly}
        ?required=${this.required}
        aria-invalid=${this.invalid ? "true" : "false"}
        @input=${this.onInput}
        @change=${this.onChange}
      >
    `
  }

  protected onInput(event: Event): void {
    event.stopPropagation()
    this.value = (event.currentTarget as HTMLInputElement).value
    emitStandardEvent(this, "input")
  }

  protected onChange(event: Event): void {
    event.stopPropagation()
    this.value = (event.currentTarget as HTMLInputElement).value
    emitStandardEvent(this, "change")
    emitValueEvent(this, "jb-change", this.value)
  }
}
