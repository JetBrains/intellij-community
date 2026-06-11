// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, nothing, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { emitStandardEvent, emitValueEvent } from "../../foundation/events"
import { buttonStyles, hostStyles, inputStyles } from "../../foundation/styles"

export class JbSpinner extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    max: { type: Number, reflect: true },
    min: { type: Number, reflect: true },
    step: { type: Number, reflect: true },
    value: { type: String, reflect: true },
  }

  static styles = [hostStyles, inputStyles, buttonStyles, css`
    .spinner {
      display: grid;
      gap: var(--jb-space-xs);
      grid-template-columns: minmax(64px, 1fr) auto auto;
    }

    .step-button {
      padding-inline: var(--jb-space-xs);
    }
  `]

  disabled = false
  max = Number.POSITIVE_INFINITY
  min = Number.NEGATIVE_INFINITY
  step = 1
  value = "0"

  render(): TemplateResult {
    return html`
      <span part="control" class="spinner">
        <input part="input" class="field-control" type="number" min=${Number.isFinite(this.min) ? this.min : nothing} max=${Number.isFinite(this.max) ? this.max : nothing} step=${this.step} .value=${this.value} ?disabled=${this.disabled} @input=${this.onInput} @change=${this.onChange}>
        <button part="decrement-button" class="button toolbar step-button" type="button" ?disabled=${this.disabled} @click=${() => this.stepValue(-1)}>-</button>
        <button part="increment-button" class="button toolbar step-button" type="button" ?disabled=${this.disabled} @click=${() => this.stepValue(1)}>+</button>
      </span>
    `
  }

  private onInput(event: Event): void {
    event.stopPropagation()
    this.value = (event.currentTarget as HTMLInputElement).value
    emitStandardEvent(this, "input")
  }

  private onChange(event: Event): void {
    event.stopPropagation()
    this.value = (event.currentTarget as HTMLInputElement).value
    emitStandardEvent(this, "change")
    emitValueEvent(this, "jb-change", this.value)
  }

  private stepValue(direction: -1 | 1): void {
    if (this.disabled) {
      return
    }
    const current = Number(this.value || 0)
    const next = Math.min(this.max, Math.max(this.min, current + direction * this.step))
    this.value = String(next)
    emitStandardEvent(this, "input")
    emitStandardEvent(this, "change")
    emitValueEvent(this, "jb-change", this.value)
  }
}

export function defineJbSpinner(registry?: CustomElementRegistryLike): void {
  defineControl("jb-spinner", JbSpinner, registry)
}
