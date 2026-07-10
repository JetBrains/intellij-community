// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { JbControlOption } from "./types"

export function normalizeOptions(options: JbControlOption[] | undefined): JbControlOption[] {
  return Array.isArray(options) ? options : []
}

export function optionLabel(options: JbControlOption[], value: string, placeholder = ""): string {
  return options.find(option => option.value === value)?.label ?? placeholder
}
