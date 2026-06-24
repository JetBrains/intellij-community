// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { css } from "lit"

export const hostStyles = css`
  :host {
    box-sizing: border-box;
    color: var(--jb-text-color);
    font-family: var(--jb-font-family);
    font-size: var(--jb-font-size);
    line-height: var(--jb-line-height);
  }

  :host([hidden]) {
    display: none !important;
  }

  *,
  *::before,
  *::after {
    box-sizing: inherit;
  }

  button,
  input,
  select,
  textarea {
    font: inherit;
  }

  [disabled],
  :host([disabled]) {
    cursor: default;
  }
`

export const buttonStyles = css`
  .button {
    appearance: none;
    align-items: center;
    background: var(--jb-bg-control);
    border: 1px solid var(--jb-border-color);
    border-radius: var(--jb-control-radius);
    color: var(--jb-text-color);
    cursor: default;
    display: inline-flex;
    gap: var(--jb-control-gap);
    justify-content: center;
    min-height: var(--jb-control-height);
    min-width: var(--jb-control-height);
    outline: none;
    padding: 0 var(--jb-control-padding-x);
    position: relative;
    user-select: none;
    white-space: nowrap;
  }

  .button:hover:not(:disabled) {
    background: var(--jb-bg-hover);
  }

  .button:active:not(:disabled),
  .button[data-pressed="true"] {
    background: var(--jb-bg-pressed);
  }

  .button:focus-visible {
    box-shadow: var(--jb-focus-ring);
  }

  .button:disabled {
    border-color: var(--jb-border-color-muted);
    color: var(--jb-text-disabled);
    opacity: 0.72;
  }

  .button.primary {
    background: var(--jb-accent-color);
    border-color: var(--jb-accent-color);
    color: var(--jb-text-on-accent);
  }

  .button.primary:hover:not(:disabled) {
    background: var(--jb-accent-hover-color);
    border-color: var(--jb-accent-hover-color);
  }

  .button.danger {
    color: var(--jb-danger-color);
  }

  .button.link {
    background: transparent;
    border-color: transparent;
    color: var(--jb-accent-text-color);
    min-height: var(--jb-control-height-compact);
    min-width: 0;
    padding: 0;
  }

  .button.link:hover:not(:disabled) {
    background: transparent;
    color: var(--jb-accent-hover-color);
    text-decoration: underline;
  }

  .button.toolbar,
  .button.icon {
    background: transparent;
    border-color: transparent;
    height: var(--jb-control-height-compact);
    min-height: var(--jb-control-height-compact);
    min-width: var(--jb-control-height-compact);
    padding: 0 var(--jb-space-xs);
  }

  .button.icon {
    width: var(--jb-control-height-compact);
  }

  .button.selected,
  .button[aria-pressed="true"] {
    background: var(--jb-bg-selected-muted);
    border-color: var(--jb-accent-soft-bg);
    color: var(--jb-text-color);
  }

  .button.small {
    min-height: var(--jb-control-height-compact);
    padding-inline: var(--jb-space-sm);
  }

  .icon-slot,
  .chevron {
    align-items: center;
    display: inline-flex;
    justify-content: center;
    line-height: 1;
  }

  .chevron {
    color: var(--jb-text-muted);
    font-size: var(--jb-font-size-small);
  }
`

export const inputStyles = css`
  .field-control,
  .textarea,
  .select {
    appearance: none;
    background: var(--jb-bg-input);
    border: 1px solid var(--jb-border-color);
    border-radius: var(--jb-control-radius);
    color: var(--jb-text-color);
    min-height: var(--jb-control-height);
    outline: none;
    padding: 0 var(--jb-control-padding-x);
    width: 100%;
  }

  .field-control:hover:not(:disabled):not([readonly]),
  .textarea:hover:not(:disabled):not([readonly]),
  .select:hover:not(:disabled) {
    border-color: var(--jb-border-color-strong);
  }

  .field-control:focus-visible,
  .textarea:focus-visible,
  .select:focus-visible {
    border-color: var(--jb-accent-color);
    box-shadow: var(--jb-focus-ring);
  }

  .field-control:disabled,
  .textarea:disabled,
  .select:disabled {
    color: var(--jb-text-disabled);
    opacity: 0.72;
  }

  .field-control[aria-invalid="true"],
  .textarea[aria-invalid="true"],
  .select[aria-invalid="true"] {
    border-color: var(--jb-danger-color);
  }

  .field-control::placeholder,
  .textarea::placeholder {
    color: var(--jb-text-secondary);
  }

  .textarea {
    line-height: var(--jb-line-height-paragraph);
    min-height: 72px;
    padding-block: var(--jb-space-xs);
    resize: vertical;
  }

  .select-wrap,
  .combo-wrap {
    position: relative;
  }

  .select {
    padding-right: 26px;
  }

  .select-wrap::after {
    color: var(--jb-text-muted);
    content: "v";
    font-size: var(--jb-font-size-small);
    pointer-events: none;
    position: absolute;
    right: 9px;
    top: 50%;
    transform: translateY(-52%);
  }
`

export const popupStyles = css`
  .popup {
    background: var(--jb-bg-panel);
    border: 1px solid var(--jb-border-color-muted);
    border-radius: var(--jb-control-radius);
    box-shadow: var(--jb-popup-shadow);
    display: grid;
    gap: 1px;
    margin-top: var(--jb-space-xs);
    min-width: 160px;
    padding: var(--jb-space-xs);
    position: absolute;
    z-index: 10;
  }

  .menu-root {
    display: inline-block;
    position: relative;
  }

  .menu-item {
    appearance: none;
    background: transparent;
    border: 0;
    border-radius: var(--jb-control-radius);
    color: var(--jb-text-color);
    min-height: var(--jb-control-height-compact);
    padding: 0 var(--jb-space-sm);
    text-align: left;
    white-space: nowrap;
  }

  .menu-item:hover:not(:disabled),
  .menu-item:focus-visible {
    background: var(--jb-bg-hover);
    outline: none;
  }

  .menu-item:disabled {
    color: var(--jb-text-disabled);
  }
`

export const choiceStyles = css`
  .choice {
    align-items: flex-start;
    color: var(--jb-text-color);
    display: inline-flex;
    gap: var(--jb-control-gap);
    min-height: var(--jb-control-height-compact);
    position: relative;
  }

  .native-check {
    height: 1px;
    left: 8px;
    opacity: 0;
    position: absolute;
    top: 8px;
    width: 1px;
  }

  .mark {
    align-items: center;
    background: var(--jb-bg-input);
    border: 1px solid var(--jb-border-color);
    color: var(--jb-text-on-accent);
    display: inline-flex;
    flex: 0 0 auto;
    height: 16px;
    justify-content: center;
    margin-top: 1px;
    width: 16px;
  }

  .checkbox .mark {
    border-radius: 3px;
  }

  .radio .mark {
    border-radius: 50%;
  }

  .native-check:focus-visible + .mark {
    box-shadow: var(--jb-focus-ring);
  }

  .native-check:checked + .mark,
  .native-check:indeterminate + .mark {
    background: var(--jb-accent-color);
    border-color: var(--jb-accent-color);
  }

  .native-check:disabled + .mark,
  .native-check:disabled ~ .choice-label {
    color: var(--jb-text-disabled);
    opacity: 0.72;
  }

  .checkbox .native-check:checked + .mark::before {
    content: "";
    border: solid currentColor;
    border-width: 0 2px 2px 0;
    height: 8px;
    margin-top: -1px;
    transform: rotate(45deg);
    width: 4px;
  }

  .checkbox .native-check:indeterminate + .mark::before {
    background: currentColor;
    content: "";
    height: 2px;
    width: 8px;
  }

  .radio .native-check:checked + .mark::before {
    background: currentColor;
    border-radius: 50%;
    content: "";
    height: 6px;
    width: 6px;
  }
`
