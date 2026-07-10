// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export const reactControlsPortalRootId = "jb-react-controls-portal-root"

export function getReactControlsPortalRoot(): HTMLElement | null {
  if (typeof document === "undefined") {
    return null
  }

  return document.getElementById(reactControlsPortalRootId)
}

export function ensureReactControlsPortalRoot(): HTMLElement | null {
  if (typeof document === "undefined") {
    return null
  }

  const existing = getReactControlsPortalRoot()
  if (existing) {
    return existing
  }

  const root = document.createElement("div")
  root.id = reactControlsPortalRootId
  root.className = "jbReactControlsPortalRoot"
  document.body.append(root)
  return root
}

