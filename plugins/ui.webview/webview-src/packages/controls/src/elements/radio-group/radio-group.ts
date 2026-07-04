// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, nothing, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { emitStandardEvent, emitValueEvent } from "../../foundation/events"
import { normalizeOptions } from "../../foundation/options"
import { hostStyles } from "../../foundation/styles"
import type { JbControlOption } from "../../foundation/types"

export class JbRadioGroup extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    items: { attribute: false },
    label: { type: String, reflect: true },
    value: { type: String, reflect: true },
  }

  static styles = [hostStyles, css`
    .group {
      border: 0;
      display: grid;
      gap: var(--jb-space-xs);
      margin: 0;
      padding: 0;
    }

    .legend {
      color: var(--jb-text-muted);
      margin-bottom: var(--jb-space-xs);
      padding: 0;
      -webkit-user-select: none;
      user-select: none;
    }
  `]

  disabled = false
  items: JbControlOption[] = []
  label = ""
  value = ""

  render(): TemplateResult {
    const options = normalizeOptions(this.items)
    return html`
      <fieldset part="group" class="group" ?disabled=${this.disabled}>
        ${this.label ? html`<legend part="label" class="legend">${this.label}</legend>` : nothing}
        ${options.length > 0 ? options.map(option => html`
          <jb-radio
            part="radio"
            value=${option.value}
            ?checked=${this.value === option.value}
            ?disabled=${this.disabled || Boolean(option.disabled)}
            @change=${() => this.selectOption(option)}
          >${option.label}</jb-radio>
        `) : html`<slot></slot>`}
      </fieldset>
    `
  }

  private selectOption(option: JbControlOption): void {
    if (this.disabled || option.disabled || this.value === option.value) {
      return
    }
    this.value = option.value
    emitStandardEvent(this, "input")
    emitStandardEvent(this, "change")
    emitValueEvent(this, "jb-change", this.value)
  }
}

export function defineJbRadioGroup(registry?: CustomElementRegistryLike): void {
  defineControl("jb-radio-group", JbRadioGroup, registry)
}
