// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { html, nothing, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { TextInputBase } from "../text-field/text-input-base"

export class JbNumberField extends TextInputBase {
  static properties = {
    ...TextInputBase.properties,
    max: { type: String, reflect: true },
    min: { type: String, reflect: true },
    step: { type: String, reflect: true },
  }

  max = ""
  min = ""
  step = ""

  protected get inputType(): string {
    return "number"
  }

  render(): TemplateResult {
    return html`
      <input
        part="input"
        class="field-control"
        type="number"
        name=${this.name || nothing}
        min=${this.min || nothing}
        max=${this.max || nothing}
        step=${this.step || nothing}
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
}

export function defineJbNumberField(registry?: CustomElementRegistryLike): void {
  defineControl("jb-number-field", JbNumberField, registry)
}
