// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { emitStandardEvent, emitValueEvent } from "../../foundation/events"
import { normalizeOptions } from "../../foundation/options"
import { buttonStyles, hostStyles } from "../../foundation/styles"
import type { JbControlOption } from "../../foundation/types"

export class JbTabs extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    items: { attribute: false },
    value: { type: String, reflect: true },
  }

  static styles = [hostStyles, buttonStyles, css`
    .tabs {
      border-bottom: 1px solid var(--jb-border-color-muted);
      display: flex;
      gap: var(--jb-space-xs);
      margin-bottom: var(--jb-space-sm);
    }

    .tab {
      border-bottom-left-radius: 0;
      border-bottom-right-radius: 0;
      margin-bottom: -1px;
    }

    .panel {
      user-select: text;
    }
  `]

  disabled = false
  items: JbControlOption[] = []
  value = ""

  render(): TemplateResult {
    return html`
      <div part="tablist" class="tabs" role="tablist">
        ${normalizeOptions(this.items).map(option => html`<button part="tab" class=${["button", "toolbar", "tab", this.value === option.value ? "selected" : ""].filter(Boolean).join(" ")} type="button" role="tab" aria-selected=${String(this.value === option.value)} ?disabled=${this.disabled || Boolean(option.disabled)} @click=${() => this.selectOption(option)}>${option.label}</button>`)}
      </div>
      <div part="panel" class="panel"><slot></slot></div>
    `
  }

  private selectOption(option: JbControlOption): void {
    if (this.disabled || option.disabled || this.value === option.value) {
      return
    }
    this.value = option.value
    emitStandardEvent(this, "change")
    emitValueEvent(this, "jb-change", this.value)
  }
}

export function defineJbTabs(registry?: CustomElementRegistryLike): void {
  defineControl("jb-tabs", JbTabs, registry)
}
