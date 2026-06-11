// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export function emitStandardEvent(host: HTMLElement, type: "input" | "change"): void {
  host.dispatchEvent(new Event(type, { bubbles: true, composed: true }))
}

export function emitValueEvent(host: HTMLElement, type: string, value: string): void {
  host.dispatchEvent(new CustomEvent(type, { detail: { value }, bubbles: true, composed: true }))
}
