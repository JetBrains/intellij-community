// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { ComponentPropsWithoutRef } from "react"

export interface JbControlChromeProps extends ComponentPropsWithoutRef<"span"> {
  compact?: boolean
  disabled?: boolean
  invalid?: boolean
}

export function JbControlChrome({ className, compact, disabled, invalid, ...props }: JbControlChromeProps) {
  return (
    <span
      {...props}
      className={["jbReactControlChrome", className].filter(Boolean).join(" ")}
      data-compact={compact ? "true" : undefined}
      data-disabled={disabled ? "true" : undefined}
      data-invalid={invalid ? "true" : undefined}
    />
  )
}

