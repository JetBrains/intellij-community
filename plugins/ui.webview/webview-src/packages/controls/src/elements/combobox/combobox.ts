// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { html, nothing, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { normalizeOptions } from "../../foundation/options"
import type { JbControlOption } from "../../foundation/types"
import { TextInputBase } from "../text-field/text-input-base"

export class JbCombobox extends TextInputBase {
  static properties = {
    ...TextInputBase.properties,
    items: { attribute: false },
  }

  items: JbControlOption[] = []

  render(): TemplateResult {
    const listId = `${this.localName}-${Math.random().toString(36).slice(2)}`
    return html`
      <span part="control" class="combo-wrap">
        <input
          part="input"
          class="field-control"
          type="text"
          list=${listId}
          name=${this.name || nothing}
          placeholder=${this.placeholder || nothing}
          .value=${this.value}
          ?disabled=${this.disabled}
          ?readonly=${this.readOnly}
          ?required=${this.required}
          aria-invalid=${this.invalid ? "true" : "false"}
          @input=${this.onInput}
          @change=${this.onChange}
        >
        <datalist id=${listId}>${normalizeOptions(this.items).map(option => html`<option value=${option.value} label=${option.label}></option>`)}</datalist>
      </span>
    `
  }
}

export function defineJbCombobox(registry?: CustomElementRegistryLike): void {
  defineControl("jb-combobox", JbCombobox, registry)
}
