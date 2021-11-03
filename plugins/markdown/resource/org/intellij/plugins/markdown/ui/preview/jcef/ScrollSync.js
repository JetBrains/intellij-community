// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
function throttle(callback, limit) {
  let waiting = false;
  return (...args) => {
    if (!waiting) {
      callback(...args);
      waiting = true;
      setTimeout(() => {
        waiting = false;
      }, limit);
    }
  };
}

class ScrollController {
  constructor() {
    this.positionAttributeName = document.querySelector(`meta[name="markdown-position-attribute-name"]`).content;
    this.collectMarkdownElements = this._collectMarkdownElements();
    const scrollHandler = throttle(() => this._scrollHandler(), 20);
    document.addEventListener('scroll', e => {
      scrollHandler()
    })
    IncrementalDOM.notifications.afterPatchListeners.push(() => {
      this.collectMarkdownElements = this._collectMarkdownElements();
    });
  }

  _collectMarkdownElements() {
    let elements = null;
    return () => {
      if (elements != null) {
        return elements;
      }
      elements = Array.from(document.body.querySelectorAll(`[${this.positionAttributeName}]`)).map(element => {
        const position = element.getAttribute(this.positionAttributeName).split("..");
        return {
          element,
          from: position[0],
          to: position[1]
        };
      });
      return elements;
    };
  }

  _scrollHandler() {
    const value = this._getElementsAtOffset(window.scrollY);
    window.__IntelliJTools.messagePipe.post("setScroll", value.previous.from);
  }

  _getNodeOffsets(node) {
    if (!node || !("getAttribute" in node)) {
      return null;
    }
    const value = node.getAttribute(this.positionAttributeName);
    if (value) {
      return value.split("..");
    }
    return null;
  }

  _findElementAtOffset(offset, node = document.body.firstChild, result = {}) {
    for (let child = node.firstChild; child !== null; child = child.nextSibling) {
      const position = this._getNodeOffsets(child);
      if (!position) {
        continue;
      }
      if (offset >= position[0] && offset <= position[1]) {
        result.element = child;
        this._findElementAtOffset(offset, child, result);
        break;
      }
    }
    return result.element;
  }

  _getElementsAtOffset(offset) {
    const elements = this.collectMarkdownElements();
    const position = offset - window.scrollY;
    let left = -1;
    let right = elements.length - 1;
    while (left + 1 < right) {
      const mid = Math.floor((left + right) / 2);
      const bounds = elements[mid].element.getBoundingClientRect();
      if (bounds.top + bounds.height >= position) {
        right = mid;
      }
      else {
        left = mid;
      }
    }
    const hiElement = elements[right];
    const hiBounds = hiElement.element.getBoundingClientRect();
    if (right >= 1 && hiBounds.top > position) {
      const loElement = elements[left];
      return { previous: loElement, next: hiElement };
    }
    if (right > 1 && right < elements.length && hiBounds.top + hiBounds.height > position) {
      return { previous: hiElement, next: elements[right + 1] };
    }
    return { previous: hiElement };
  }

  scrollTo(offset, smooth = true) {
    if (this.currentScrollElement) {
      const position = this._getNodeOffsets(this.currentScrollElement);
      if (offset >= position[0] && offset <= position[1]) {
        return;
      }
    }
    const body = document.body;
    if (!body || !body.firstChild || !body.firstChild.firstChild) {
      return;
    }
    this.currentScrollElement = this._findElementAtOffset(offset);
    if (!this.currentScrollElement) {
      // console.warn(`Failed to find element for offset: ${offset}`);
      return;
    }
    if (!smooth) {
      this.currentScrollElement.scrollIntoView();
    }
    else {
      this.currentScrollElement.scrollIntoView({
        behavior: "smooth"
      });
    }
  }
}

window.scrollController = new ScrollController();
