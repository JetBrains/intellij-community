// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, html, nothing, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { emitStandardEvent, emitValueEvent } from "../../foundation/events"
import { hostStyles, inputStyles } from "../../foundation/styles"

export class JbTextArea extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    invalid: { type: Boolean, reflect: true },
    name: { type: String, reflect: true },
    placeholder: { type: String, reflect: true },
    readOnly: { type: Boolean, reflect: true, attribute: "readonly" },
    required: { type: Boolean, reflect: true },
    rows: { type: Number, reflect: true },
    value: { type: String, reflect: true },
  }

  static styles = [hostStyles, inputStyles]

  disabled = false
  invalid = false
  name = ""
  placeholder = ""
  readOnly = false
  required = false
  rows = 4
  value = ""

  render(): TemplateResult {
    return html`
      <textarea
        part="textarea"
        class="textarea"
        name=${this.name || nothing}
        placeholder=${this.placeholder || nothing}
        rows=${this.rows}
        .value=${this.value}
        ?disabled=${this.disabled}
        ?readonly=${this.readOnly}
        ?required=${this.required}
        aria-invalid=${this.invalid ? "true" : "false"}
        @input=${this.onInput}
        @change=${this.onChange}
      ></textarea>
    `
  }

  private onInput(event: Event): void {
    event.stopPropagation()
    this.value = (event.currentTarget as HTMLTextAreaElement).value
    emitStandardEvent(this, "input")
  }

  private onChange(event: Event): void {
    event.stopPropagation()
    this.value = (event.currentTarget as HTMLTextAreaElement).value
    emitStandardEvent(this, "change")
    emitValueEvent(this, "jb-change", this.value)
  }
}

export function defineJbTextArea(registry?: CustomElementRegistryLike): void {
  defineControl("jb-text-area", JbTextArea, registry)
}
