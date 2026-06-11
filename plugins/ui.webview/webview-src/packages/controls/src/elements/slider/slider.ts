// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { emitStandardEvent, emitValueEvent } from "../../foundation/events"
import { hostStyles } from "../../foundation/styles"

export class JbSlider extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    max: { type: Number, reflect: true },
    min: { type: Number, reflect: true },
    step: { type: Number, reflect: true },
    value: { type: String, reflect: true },
  }

  static styles = [hostStyles, css`
    .slider {
      accent-color: var(--jb-accent-color);
      width: 100%;
    }
  `]

  disabled = false
  max = 100
  min = 0
  step = 1
  value = "0"

  render(): TemplateResult {
    return html`<input part="input" class="slider" type="range" min=${this.min} max=${this.max} step=${this.step} .value=${this.value} ?disabled=${this.disabled} @input=${this.onInput} @change=${this.onChange}>`
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
}

export function defineJbSlider(registry?: CustomElementRegistryLike): void {
  defineControl("jb-slider", JbSlider, registry)
}
