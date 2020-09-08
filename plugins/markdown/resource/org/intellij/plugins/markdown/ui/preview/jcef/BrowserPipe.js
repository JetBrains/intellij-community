// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
"use strict";

(function() {
  /**
   * Message passing interface for sending/receiving messages
   * between browser and IDE
   */
  class MessagePipe {
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
     * API for IDE for posting and subscribing to events
     */
    ideApi = {
      /**
       * @private
       * @type {Object.<string, [EventCallback]>}
       */
      listeners: {},

      /**
       * Post event for browser listeners. Call this in CefBrowser::executeJavaScript.
       * @param {string} tag
       * @param {string} data
       */
      post: (tag, data) => {
        if (!this.ideApi.listeners[tag]) {
          throw new Error(`Could not post event with tag: ${tag}!`);
        }
        this.ideApi.listeners[tag].forEach(listener => {
          try {
            listener.call(null, data);
          }
          catch (error) {
            console.warn(`Failed to call listener for event with tag: ${tag}!`, error);
          }
        });
      },

      /**
       * Subscribes to events with {@link tag} posted by browser code
       * @param {string} tag
       * @param {EventCallback} callback Result of <pre><code>JBCefJSCallback.inject("data")</code></pre>
       */
      subscribe: (tag, callback) => {
        if (!this.listeners[tag]) {
          this.listeners[tag] = [];
        }
        this.listeners[tag].push(callback);
      }
    };

    /**
     * Subscribes to events posted by IDE code
     * @param {string} tag
     * @param {EventCallback} callback
     */
    subscribe(tag, callback) {
      if (!this.ideApi.listeners[tag]) {
        this.ideApi.listeners[tag] = [];
      }
      this.ideApi.listeners[tag].push(callback);
    }

    /**
     * Posts event for IDE listeners
     * @param {string} tag
     * @param {string} data
     */
    post(tag, data) {
      if (!this.listeners[tag]) {
        throw new Error(`Could not call listener for event with tag: ${tag}!`);
      }
      this.listeners[tag].forEach(listener => {
        try {
          listener.call(null, data);
        }
        catch (error) {
          console.warn(`Failed to call listener for event with tag: ${tag}!`, error);
        }
      });
    }
  }

  /**
   * @type {MessagePipe}
   */
  window.messagePipe = new MessagePipe();
})();
