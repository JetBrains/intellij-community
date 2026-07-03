// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useEffect, useRef } from "react"
import { addWebViewFocusLeaveListener } from "../../../api/src/focus"

export function useWebViewFocusLeave(listener: () => void, enabled = true): void {
  const listenerRef = useRef(listener)
  listenerRef.current = listener

  useEffect(() => {
    if (!enabled) {
      return undefined
    }

    return addWebViewFocusLeaveListener(() => listenerRef.current())
  }, [enabled])
}
