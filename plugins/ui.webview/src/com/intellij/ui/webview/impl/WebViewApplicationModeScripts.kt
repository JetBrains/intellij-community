// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl

import org.intellij.lang.annotations.Language

internal object WebViewApplicationModeScripts {
  /**
   * Document-start script for embedded application-mode WebViews.
   *
   * Native backend settings disable engine-level browser features where the platform exposes them,
   * but some browser-like behavior is controlled only by page DOM state. This script applies that
   * DOM layer: it suppresses the browser context menu and keeps native input assist hints disabled
   * for regular controls, dynamically rendered controls, and controls inside open shadow roots.
   * It intentionally avoids stopping normal application events, so component-owned input handling
   * and custom autocomplete UI can keep working.
   */
  @Language("JavaScript")
  val DOM_HARDENING_SCRIPT: String = """
    (function() {
      const formControlSelector = 'input, textarea, select';
      const inputAssistAttributes = [
        ['autocomplete', 'off'],
        ['autocorrect', 'off'],
        ['autocapitalize', 'off'],
        ['spellcheck', 'false']
      ];
      const observedRoots = new WeakSet();

      function isElement(node) {
        return node && node.nodeType === Node.ELEMENT_NODE;
      }

      function isFormControl(node) {
        return isElement(node) && typeof node.matches === 'function' && node.matches(formControlSelector);
      }

      function setAttributeIfNeeded(element, name, value) {
        if (element.getAttribute(name) !== value) {
          element.setAttribute(name, value);
        }
      }

      function configureFormControl(element) {
        for (const [name, value] of inputAssistAttributes) {
          setAttributeIfNeeded(element, name, value);
        }
        // The attribute is the browser hint; the property keeps WebKit spellchecking state in sync.
        if (element.spellcheck !== false) {
          element.spellcheck = false;
        }
      }

      function configureTree(root) {
        if (!root) {
          return;
        }

        if (isFormControl(root)) {
          configureFormControl(root);
        }

        // Open shadow roots are reachable from page JS; closed roots stay component-owned.
        if (isElement(root) && root.shadowRoot) {
          configureTree(root.shadowRoot);
          observeRoot(root.shadowRoot);
        }

        if (typeof root.querySelectorAll !== 'function') {
          return;
        }

        for (const element of root.querySelectorAll(formControlSelector)) {
          configureFormControl(element);
        }

        for (const element of root.querySelectorAll('*')) {
          if (element.shadowRoot) {
            configureTree(element.shadowRoot);
            observeRoot(element.shadowRoot);
          }
        }
      }

      function observeRoot(root) {
        if (!root || observedRoots.has(root) || typeof MutationObserver !== 'function') {
          return;
        }
        observedRoots.add(root);

        // Framework renders can add controls or restore attributes after our document-start pass.
        const observer = new MutationObserver(function(mutations) {
          for (const mutation of mutations) {
            if (mutation.type === 'attributes') {
              if (isFormControl(mutation.target)) {
                configureFormControl(mutation.target);
              }
              continue;
            }

            for (const node of mutation.addedNodes) {
              configureTree(node);
            }
          }
        });

        observer.observe(root, {
          childList: true,
          subtree: true,
          attributes: true,
          attributeFilter: inputAssistAttributes.map(function(entry) { return entry[0]; })
        });
      }

      function eventPath(event) {
        if (typeof event.composedPath === 'function') {
          return event.composedPath();
        }

        const path = [];
        let node = event.target;
        while (node) {
          path.push(node);
          node = node.parentNode || node.host || null;
        }
        return path;
      }

      function configureFromEventPath(event) {
        // Covers focus/input that happens before an observer turn without cancelling app events.
        for (const node of eventPath(event)) {
          if (isFormControl(node)) {
            configureFormControl(node);
          }
          if (isElement(node) && node.shadowRoot) {
            configureTree(node.shadowRoot);
            observeRoot(node.shadowRoot);
          }
        }
      }

      function preventCancelableDefault(event) {
        if (event.cancelable) {
          event.preventDefault();
        }
      }

      function patchAttachShadow() {
        // Catch open shadow roots created after document-start, such as Lit component renders.
        const prototype = typeof Element !== 'undefined' ? Element.prototype : null;
        if (!prototype || prototype['__ideaApplicationModeAttachShadowPatched'] || typeof prototype.attachShadow !== 'function') {
          return;
        }

        const originalAttachShadow = prototype.attachShadow;
        try {
          Object.defineProperty(prototype, '__ideaApplicationModeAttachShadowPatched', { value: true });
          Object.defineProperty(prototype, 'attachShadow', {
            configurable: true,
            writable: true,
            value: function(init) {
              const root = originalAttachShadow.apply(this, arguments);
              if (root && init && init.mode === 'open') {
                configureTree(root);
                observeRoot(root);
              }
              return root;
            }
          });
        }
        catch (_) {
        }
      }

      document.addEventListener('contextmenu', preventCancelableDefault, true);
      document.addEventListener('focusin', configureFromEventPath, true);
      document.addEventListener('beforeinput', configureFromEventPath, true);
      document.addEventListener('input', configureFromEventPath, true);

      patchAttachShadow();
      observeRoot(document);
      configureTree(document);
      document.addEventListener('DOMContentLoaded', function() {
        observeRoot(document);
        configureTree(document);
      }, { once: true });
    })();
  """.trimIndent()
}
