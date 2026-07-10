// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { emitStandardEvent, emitValueEvent } from "../../foundation/events"
import { normalizeOptions } from "../../foundation/options"
import { buttonStyles, hostStyles } from "../../foundation/styles"
import type { JbControlOption } from "../../foundation/types"

export class JbSegmentedControl extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    items: { attribute: false },
    value: { type: String, reflect: true },
  }

  static styles = [hostStyles, buttonStyles, css`
    .segments {
      align-items: center;
      background: var(--jb-bg-control);
      border: 1px solid var(--jb-border-color-muted);
      border-radius: var(--jb-control-radius);
      display: inline-flex;
      padding: 1px;
    }

    .segment {
      border-color: transparent;
      border-radius: 3px;
      min-width: 0;
    }
  `]

  disabled = false
  items: JbControlOption[] = []
  value = ""

  render(): TemplateResult {
    return html`
      <div part="group" class="segments" role="radiogroup">
        ${normalizeOptions(this.items).map(option => html`
          <button part="segment" class=${["button", "toolbar", "segment", this.value === option.value ? "selected" : ""].filter(Boolean).join(" ")} type="button" role="radio" aria-checked=${String(this.value === option.value)} ?disabled=${this.disabled || Boolean(option.disabled)} @click=${() => this.selectOption(option)}>${option.label}</button>
        `)}
      </div>
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

export function defineJbSegmentedControl(registry?: CustomElementRegistryLike): void {
  defineControl("jb-segmented-control", JbSegmentedControl, registry)
}
