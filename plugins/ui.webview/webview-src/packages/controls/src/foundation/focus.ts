// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { ReactiveController, ReactiveControllerHost } from "lit"

export const WEBVIEW_FOCUS_LEAVE_EVENT = "wvi-focus-leave"

export class WebViewFocusLeaveController implements ReactiveController {
  private readonly listener = (): void => this.onFocusLeave()

  constructor(
    host: ReactiveControllerHost,
    private readonly onFocusLeave: () => void,
  ) {
    host.addController(this)
  }

  hostConnected(): void {
    window.addEventListener(WEBVIEW_FOCUS_LEAVE_EVENT, this.listener)
  }

  hostDisconnected(): void {
    window.removeEventListener(WEBVIEW_FOCUS_LEAVE_EVENT, this.listener)
  }
}
