// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class ScrollController {
  #lastOffset = 0;
  #scrollFinished = true;
  // #nextScrollElement = null;

  constructor() {
    this.positionAttributeName = document.querySelector(`meta[name="markdown-position-attribute-name"]`).content;
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
    window.__IntelliJTools.messagePipe.post("setScroll", value.previous.from);
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

  #doScroll(element, smooth) {
    if (!smooth) {
      element.scrollIntoView();
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

  scrollTo(offset, smooth = true) {
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
    this.#doScroll(element, smooth);
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
    return new Promise( (resolve) => {
      let frames = 0;
      let lastPosition = null;
      element.scrollIntoView({
        behavior: "smooth"
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
