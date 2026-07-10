// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { nothing } from "lit"

export function boolAttribute(value: boolean): "true" | typeof nothing {
  return value ? "true" : nothing
}
