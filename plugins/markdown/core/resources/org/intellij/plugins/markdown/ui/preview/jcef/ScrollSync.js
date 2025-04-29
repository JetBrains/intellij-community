// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class ScrollController {
  #lastOffset = 0;
  #scrollFinished = true;
  // #nextScrollElement = null;

  constructor() {
    this.positionAttributeName = document.querySelector(`meta[name="markdown-position-attribute-name"]`).content;
    this.setScrollEventName = document.querySelector(`meta[name="markdown-set-scroll-event-name"]`).content;
    this.collectMarkdownElements = this.#doCollectMarkdownElements();
    IncrementalDOM.notifications.afterPatchListeners.push(() => {
      this.collectMarkdownElements = this.#doCollectMarkdownElements();
    });
    const scrollHandler = ScrollController.#throttle(() => this.#scrollHandler(), 20);
    document.addEventListener("scroll", event => scrollHandler());
  }

  #doCollectMarkdownElements() {
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

  #scrollHandler() {
    const value = this._getElementsAtOffset(window.scrollY);
    window.__IntelliJTools.messagePipe.post(this.setScrollEventName, value.previous.from);
  }

  getNodeOffsets(node) {
    if (!node || !("getAttribute" in node)) {
      return null;
    }
    const value = node.getAttribute(this.positionAttributeName);
    if (value) {
      return value.split("..");
    }
    return null;
  }

  getMaxOffset() {
    const element = document.body.firstChild;
    const offsets = this.getNodeOffsets(element);
    if (!offsets) {
      throw new Error("First body child is expected to be the root of the document!");
    }
    return offsets[1];
  }

  #findElementAtOffset(offset, node = document.body.firstChild, result = {}) {
    for (let child = node.firstChild; child !== null; child = child.nextSibling) {
      if (child.nodeType !== Node.ELEMENT_NODE) {
        continue;
      }
      const position = this.getNodeOffsets(child);
      if (!position) {
        continue;
      }
      if (offset >= position[0] && offset <= position[1]) {
        result.element = child;
        this.#findElementAtOffset(offset, child, result);
        break;
      }
    }
    return result.element;
  }

  #actuallyFindElement(offset, forward = false) {
    const targetElement = this.#findElementAtOffset(offset);
    if (targetElement) {
      return targetElement;
    }
    if (forward) {
      const maxOffset = this.getMaxOffset();
      for (let it = offset; it <= maxOffset; it += 1) {
        const previousElement = this.#findElementAtOffset(it);
        if (previousElement) {
          return previousElement;
        }
      }
    } else {
      for (let it = offset - 1; it >= 0; it -= 1) {
        const previousElement = this.#findElementAtOffset(it);
        if (previousElement) {
          return previousElement;
        }
      }
    }
    return null;
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

  #isElementVisible(element) {
    const rect = element.getBoundingClientRect();

    return (
      rect.top >= 0 &&
      rect.bottom <= (window.innerHeight || document.documentElement.clientHeight)
    );
  }

  #doScroll(element, smooth, doNothingIfAlreadyOnScreen = false) {
    if (doNothingIfAlreadyOnScreen && this.#isElementVisible(element))
      return;

    if (!smooth) {
      element.scrollIntoView(element.getBoundingClientRect().top < 0);
      return;
    }

    this.#scrollFinished = false;
    ScrollController.#performSmoothScroll(element).then(() => {
      this.#scrollFinished = true;
    });
  }

  // #doScroll(element, smooth) {
  //   if (!smooth) {
  //     element.scrollIntoView();
  //     return;
  //   }
  //   if (!this.#scrollFinished) {
  //     this.#nextScrollElement = element;
  //     return;
  //   }
  //   this.#scrollFinished = false;
  //   const resolve = () => {
  //     this.#scrollFinished = true;
  //     if (this.#nextScrollElement) {
  //       const element = this.#nextScrollElement;
  //       this.#nextScrollElement = null;
  //       this.#doScroll(element, true).then(resolve);
  //     }
  //   };
  //   return ScrollController.#performSmoothScroll(element).then(resolve);
  // }

  scrollBy(horizontal, vertical) {
    if (this.#scrollFinished) {
      window.scrollBy(horizontal, vertical);
    }
  }

  scrollTo(offset, smooth = true, doNothingIfAlreadyOnScreen = false) {
    if (this.currentScrollElement) {
      const position = this.getNodeOffsets(this.currentScrollElement);
      if (offset >= position[0] && offset <= position[1]) {
        return;
      }
    }
    const body = document.body;
    if (!body || !body.firstChild || !body.firstChild.firstChild) {
      return;
    }
    const element = this.#actuallyFindElement(offset, offset >= this.#lastOffset);
    this.#lastOffset = offset;
    if (!element) {
      console.warn(`Failed to find element for offset: ${offset}`);
      return;
    }
    this.currentScrollElement = element;
    this.#doScroll(element, smooth, doNothingIfAlreadyOnScreen);
  }

  ensureMarkdownSrcOffsetIsVisible(offset) {
    // Find an element with the narrowest range inclusive of `offset`
    const elements = this.collectMarkdownElements();
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

    if (!element && !fallbackElement) {
      console.info(`*** no match at ${offset} ***`);
      return;
    }
    else if (!element)
      element = fallbackElement;

    const rect = element.getBoundingClientRect();
    console.info(`*** match: ${offset}, ${element.localName}, height: ${rect.height}, span: ${minSpan} ***`);
    this.#doScroll(element, true, true);
  }

  static #throttle(callback, limit) {
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

  static #performSmoothScroll(element) {
    return new Promise((resolve) => {
      let frames = 0;
      let lastPosition = null;
      element.scrollIntoView({
        behavior: 'smooth',
        block: element.getBoundingClientRect().top < 0 ? 'start' : 'end',
        inline: 'nearest'
      });
      const action = () => {
        const currentPosition = element.getBoundingClientRect().top;
        if (currentPosition === lastPosition) {
          frames += 1;
          if (frames > 2) {
            return resolve();
          }
        } else {
          frames = 0;
          lastPosition = currentPosition;
        }
        requestAnimationFrame(action);
      };
      requestAnimationFrame(action);
    });
  }
}

window.scrollController = new ScrollController();
