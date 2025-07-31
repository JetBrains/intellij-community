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

  // Primary sort by ascending starting offset, secondary sort by ascending range size.
  #elementSorter = (a, b) => {
    const diff = a.from - b.from;

    return diff !== 0 ? diff : a.to - b.to;
  }

  #doCollectMarkdownElements() {
    return () => {
      const elements = Array.from(document.body.querySelectorAll(`[${this.positionAttributeName}]`)).map(element => {
        const position = element.getAttribute(this.positionAttributeName).split("..");
        return {
          element, // Normally holds a single DOM element, but might be a before/after pair of elements.
          from: parseInt(position[0]),
          to: parseInt(position[1])
        };
      }).sort(this.#elementSorter);

      // Find unmapped source ranges (mostly untagged newlines inside the root <div>).
      let lastFrom = -1;

      for (let i = 0; i < elements.length; i++) {
        const element = elements[i];

        if (lastFrom !== element.from) {
          if (lastFrom >= 0 && element.from > lastFrom + 1) {
            elements.splice(i++, 0, {
              element: { before: elements[i - 1].element, after: element.element },
              from: lastFrom + 1,
              to: element.from - 1
            });
          }

          lastFrom = element.from;
        }
      }

      return elements;
    };
  }

  #getCachedMarkdownElements() {
    return this.#cachedMarkdownElements || (this.#cachedMarkdownElements = this.collectMarkdownElements());
  }

  clearMarkdownElementsCache() {
    this.#cachedMarkdownElements = null;
  }

  #doScroll(elementOrRect, forceSmooth = false) {
    let top, bottom;
    const wh = window.innerHeight;

    if (elementOrRect instanceof Element) {
      // If the element has descendants which add to its height, for scrolling-into-view purposes treat the
      // parent element as if its height is reduced by its range-marked descendants.
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
      top = elementOrRect.before?.getBoundingClientRect()?.bottom ?? Number.MAX_SAFE_INTEGER;
      bottom = elementOrRect.after?.getBoundingClientRect()?.top ?? Number.MAX_SAFE_INTEGER;
    }

    // Element rectangle already on screen?
    if ((top >= 0 && bottom <= wh) || top === Number.MAX_SAFE_INTEGER || bottom === Number.MAX_SAFE_INTEGER)
      return;

    const extraScroll = Math.min(this.#extraScroll, wh / 25);
    const now = performance.now();
    let behavior = 'smooth';
    let delta = bottom > wh ? bottom - wh + extraScroll : top - extraScroll;

    // For large jumps or rapid-fire scrolling, using instant scrolling.
    if (!forceSmooth && (now - this.#lastScrollTime < 250 || Math.abs(delta) > wh / 2))
      behavior = 'instant';

    window.scrollBy({ left: 0, top: delta, behavior });
    this.#lastScrollTime = now;
  }

  #findFirstWithOffset(elements, offset) {
    let low = 0;
    let high = elements.length - 1;
    let index;

    while (low <= high) {
      index = Math.floor((low + high) / 2);

      if (elements[index].from === offset)
        break;
      else if (elements[index].from < offset)
        low = index + 1;
      else
        high = index - 1;
    }

    while (index > 0 && elements[index - 1].from === offset)
      --index;

    return index;
  }

  async #waitForStableSizeAndScrollPosition() {
    return new Promise(resolve => {
      let stableCount = 0;
      let lastYOffset = window.scrollY;
      let lastHeight = document.documentElement.getBoundingClientRect().height;

      const checkStability = () => {
        const yOffset = window.scrollY;
        const height = document.documentElement.getBoundingClientRect().height;

        if (lastYOffset === yOffset && lastHeight === height && ++stableCount > 3)
          resolve();
        else {
          lastYOffset = yOffset;
          lastHeight = height;
          requestAnimationFrame(checkStability);
        }
      }

      requestAnimationFrame(checkStability);
    });
  }

  async ensureMarkdownSrcOffsetIsVisible(offset, smooth = false, whenStable = false) {
    if (whenStable)
      await this.#waitForStableSizeAndScrollPosition();

    // Find an element with the narrowest range inclusive of `offset`
    const elements = this.#getCachedMarkdownElements();
    let element;
    let e;
    let minSpan = Number.MAX_SAFE_INTEGER;
    let fallbackElement;

    for (let i = this.#findFirstWithOffset(elements, offset); i < elements.length; ++i) {
      const elem = elements[i];

      if (!fallbackElement && elem.from >= offset)
        fallbackElement = elem.element;

      if (elem.element.localName !== 'div' && elem.from <= offset && offset <= elem.to && elem.to - elem.from < minSpan) {
        e = elem;
        element = elem.element;
        minSpan = elem.to - elem.from;
      }
      else if (elem.from > offset)
        break;
    }

    if (!element && !fallbackElement)
      return;
    else if (!element)
      element = fallbackElement;

    this.#doScroll(element, smooth);
  }
}

window.scrollController = new ScrollController();
