// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// noinspection JSUnresolvedReference

class ScrollController {
  #cachedMarkdownElements = null;
  #extraScroll = 50;
  #lastScrollTime = 0;
  #roughPartialLine = 12;

  constructor() {
    this.positionAttributeName = document.querySelector(`meta[name="markdown-position-attribute-name"]`).content;
    this.collectMarkdownElements = this.#doCollectMarkdownElements();
    IncrementalDOM.notifications.afterPatchListeners.push(() => {
      this.#cachedMarkdownElements = null;
      this.collectMarkdownElements = this.#doCollectMarkdownElements();
    });
  }

  #doCollectMarkdownElements() {
    return () => {
      return Array.from(document.body.querySelectorAll(`[${this.positionAttributeName}]`)).map(element => {
        const position = element.getAttribute(this.positionAttributeName).split("..");
        return {
          element,
          from: position[0],
          to: position[1]
        };
      });
    };
  }

  #getCachedMarkdownElements() {
    return this.#cachedMarkdownElements || (this.#cachedMarkdownElements = this.collectMarkdownElements());
  }

  clearMarkdownElementsCache() {
    this.#cachedMarkdownElements = null;
  }

  #doScroll(elementOrRect) {
    let top, bottom;
    const wh = window.innerHeight;

    if (elementOrRect instanceof Element) {
      // If the element has descendants which add to its height, for scrolling-into-view purposes treat the
      // parent element as if it has height reduced by its range-marked descendants.
      const rect = elementOrRect.getBoundingClientRect();

      top = rect.top;
      bottom = rect.bottom;

      const checkChildren = (element) => {
        for (const child of element.children) {
          if (child.hasAttribute(this.positionAttributeName)) {
            const childRect = child.getBoundingClientRect();

            if (childRect.top > rect.top + this.#roughPartialLine) {
              bottom = childRect.top;
              break;
            }
          }

          if (child.children)
            checkChildren(child);
        }
      };

      checkChildren(elementOrRect);
    }
    else {
      top = elementOrRect.top;
      bottom = elementOrRect.bottom;
    }

    // Element rectangle already on screen?
    if (top >= 0 && bottom <= wh)
      return;

    const extraScroll = Math.min(this.#extraScroll, wh / 25);
    const now = performance.now();
    let behavior = 'smooth';
    let delta = bottom > wh ? bottom - wh + extraScroll : top - extraScroll;

    // For large jumps or rapid-fire scrolling, using instant scrolling.
    if (now - this.#lastScrollTime < 250 || Math.abs(delta) > wh / 2)
      behavior = 'instant';

    window.scrollBy({ left: 0, top: delta, behavior });
    this.#lastScrollTime = now;
  }

  ensureMarkdownSrcOffsetIsVisible(offset) {
    // Find an element with the narrowest range inclusive of `offset`
    const elements = this.#getCachedMarkdownElements();
    let element;
    let e;
    let minSpan = Number.MAX_SAFE_INTEGER;
    let fallbackElement;

    for (const elem of elements) {
      if (!fallbackElement && elem.from >= offset)
        fallbackElement = elem.element;

      if (elem.element.localName !== 'div' && elem.from <= offset && offset <= elem.to && elem.to - elem.from < minSpan) {
        e = elem;
        element = elem.element;
        minSpan = elem.to - elem.from;
      }
    }

    if (!element && !fallbackElement)
      return;
    else if (!element)
      element = fallbackElement;

    this.#doScroll(element);
  }
}

window.scrollController = new ScrollController();
