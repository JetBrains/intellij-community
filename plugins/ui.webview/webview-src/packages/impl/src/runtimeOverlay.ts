// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

const RUNTIME_OVERLAY_ID = "__wvi-runtime-overlay"

export function applyRuntimeInfo(params: unknown): void {
  const runtimeInfo = params && typeof params === "object" ? params as { overlayVisible?: unknown; displayName?: unknown } : undefined
  if (!runtimeInfo || runtimeInfo.overlayVisible !== true) {
    removeRuntimeOverlay()
    return
  }

  const displayName = typeof runtimeInfo.displayName === "string" ? runtimeInfo.displayName.trim() : ""
  if (displayName.length === 0) {
    removeRuntimeOverlay()
    return
  }

  ensureRuntimeOverlay(displayName)
}

function ensureRuntimeOverlay(displayName: string): void {
  if (!document.body) {
    document.addEventListener("DOMContentLoaded", function onRuntimeOverlayReady() {
      document.removeEventListener("DOMContentLoaded", onRuntimeOverlayReady)
      ensureRuntimeOverlay(displayName)
    })
    return
  }

  let overlay = document.getElementById(RUNTIME_OVERLAY_ID)
  if (!overlay) {
    overlay = document.createElement("div")
    overlay.id = RUNTIME_OVERLAY_ID
    overlay.style.position = "fixed"
    overlay.style.right = "6px"
    overlay.style.bottom = "4px"
    overlay.style.zIndex = "2147483647"
    overlay.style.pointerEvents = "none"
    overlay.style.userSelect = "none"
    overlay.style.padding = "2px 5px"
    overlay.style.borderRadius = "4px"
    overlay.style.background = "rgba(0, 0, 0, 0.52)"
    overlay.style.color = "rgba(255, 255, 255, 0.86)"
    overlay.style.font = "11px/14px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
    overlay.style.letterSpacing = "0"
    overlay.style.whiteSpace = "nowrap"
    document.body.appendChild(overlay)
  }
  overlay.textContent = displayName
}

function removeRuntimeOverlay(): void {
  const overlay = document.getElementById(RUNTIME_OVERLAY_ID)
  overlay?.parentNode?.removeChild(overlay)
}
