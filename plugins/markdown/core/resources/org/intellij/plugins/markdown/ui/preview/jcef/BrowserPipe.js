// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
"use strict";

(function() {
  /**
   * Message passing interface for sending/receiving messages between browser and IDE.
   */
  class JcefMessagePipeImpl {
    /**
     * @callback EventCallback
     * @param {string} data
     */

    /**
     * @private
     * @type {Object.<string, [EventCallback]>}
     */
    listeners = {};

    /**
     * Subscribes to events posted by IDE code
     * @param {string} tag
     * @param {EventCallback} callback
     */
    subscribe(tag, callback) {
      if (!this.listeners[tag]) {
        this.listeners[tag] = [];
      }
      this.listeners[tag].push(callback);
    }

    /**
     * Posts event for IDE listeners
     * @param {string} tag
     * @param {string} data
     */
    post(tag, data) {
      try {
        window['__IntelliJTools']["___jcefMessagePipePostToIdeFunction"](JSON.stringify({type: tag, data}));
      } catch (error) {
        console.error(error);
      }
    }

    callBrowserListeners({type, data}) {
      const listeners = this.listeners[type];
      if (!listeners) {
        console.warn(`No listeners for messages with tag: ${type}`);
        return;
      }
      for (const listener of listeners) {
        try {
          listener(data);
        } catch (error) {
          console.log(`Error occurred while calling listener for ${type}`);
          console.error(error);
        }
      }
    }
  }

  if (window.__IntelliJTools === undefined) {
    window.__IntelliJTools = {};
  }

  /**
   * @type {JcefMessagePipeImpl}
   */
  window.__IntelliJTools.messagePipe = new JcefMessagePipeImpl();

  window.addEventListener("IdeReady", () => window.__IntelliJTools.messagePipe.post("documentReady", ""));
})();
