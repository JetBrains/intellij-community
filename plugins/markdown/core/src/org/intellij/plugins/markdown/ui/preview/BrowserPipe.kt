// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.Disposable

/**
 * Base interface for communication channels with browser-based previews.
 *
 * On the browser side you can use: `window.__IntelliJTools.messagePipe.post(type: string, data: string)` and
 * `window.__IntelliJTools.messagePipe.subscribe(type: string, callback: (data) => void)`
 * for the same mirrored functionality.
 *
 * Note: Should be disposed before the actual preview.
 */
interface BrowserPipe: Disposable {
  fun interface Handler {
    fun messageReceived(data: String)
  }

  /**
   * Sends message with type [type] and data [data] to the preview.
   *
   * @param type Message type
   * @param data Message data
   */
  fun send(type: String, data: String)

  /**
   * Subscribes to receiving messages with type [type].
   *
   * @param type Message type
   * @param handler A handler that will be called on receiving message with type [type].
   */
  fun subscribe(type: String, handler: Handler)

  /**
   * Removes subscription for messages with type [type] and handler [handler].
   */
  fun removeSubscription(type: String, handler: Handler)
}
