// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { LitElement, css, html, nothing, type TemplateResult } from "lit"
import { defineControl, type CustomElementRegistryLike } from "../../foundation/define"
import { emitStandardEvent, emitValueEvent } from "../../foundation/events"
import { buttonStyles, hostStyles, inputStyles } from "../../foundation/styles"

export class JbExpandableTextField extends LitElement {
  static properties = {
    disabled: { type: Boolean, reflect: true },
    expanded: { type: Boolean, reflect: true },
    placeholder: { type: String, reflect: true },
    readOnly: { type: Boolean, reflect: true, attribute: "readonly" },
    value: { type: String, reflect: true },
  }

  static styles = [hostStyles, inputStyles, buttonStyles, css`
    .expandable {
      display: grid;
      gap: var(--jb-space-xs);
      grid-template-columns: minmax(80px, 1fr) auto;
    }

    .expanded {
      grid-column: 1 / -1;
    }

    .button.expand-button {
      height: var(--jb-control-height);
      min-height: var(--jb-control-height);
      min-width: var(--jb-control-height);
      padding: 0;
      width: var(--jb-control-height);
    }
  `]

  disabled = false
  expanded = false
  placeholder = ""
  readOnly = false
  value = ""

  render(): TemplateResult {
    return html`
      <span part="root" class="expandable">
        ${this.expanded ? html`
          <textarea part="textarea" class="textarea expanded" placeholder=${this.placeholder || nothing} .value=${this.value} ?disabled=${this.disabled} ?readonly=${this.readOnly} @input=${this.onTextAreaInput} @change=${this.onTextAreaChange}></textarea>
        ` : html`
          <input part="input" class="field-control" type="text" placeholder=${this.placeholder || nothing} .value=${this.value} ?disabled=${this.disabled} ?readonly=${this.readOnly} @input=${this.onInput} @change=${this.onChange}>
        `}
        <button
          part="expand-button"
          class="button toolbar expand-button"
          type="button"
          ?disabled=${this.disabled}
          aria-label=${this.expanded ? "Collapse" : "Expand"}
          aria-expanded=${String(this.expanded)}
          @click=${() => this.expanded = !this.expanded}
        >
          <span class=${this.expanded ? "button-icon minus" : "button-icon plus"} aria-hidden="true"></span>
        </button>
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

  private onTextAreaInput(event: Event): void {
    event.stopPropagation()
    this.value = (event.currentTarget as HTMLTextAreaElement).value
    emitStandardEvent(this, "input")
  }

  private onTextAreaChange(event: Event): void {
    event.stopPropagation()
    this.value = (event.currentTarget as HTMLTextAreaElement).value
    emitStandardEvent(this, "change")
    emitValueEvent(this, "jb-change", this.value)
  }
}

export function defineJbExpandableTextField(registry?: CustomElementRegistryLike): void {
  defineControl("jb-expandable-text-field", JbExpandableTextField, registry)
}
