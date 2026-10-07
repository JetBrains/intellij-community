// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class ScrollController {
  #lastOffset = 0;
  #scrollFinished = true;
  #targetSourceOffset = 0;
  #isFrameRequested = false;
  #followY = null;
  #appliedY = 0;
  #lastFrameTime = 0;
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

  scrollToSourceOffset(offset) {
    this.#targetSourceOffset = offset;
    if (this.#isFrameRequested) {
      return;
    }
    this.#isFrameRequested = true;
    this.#lastFrameTime = performance.now();
    requestAnimationFrame(time => this.#followFrame(time));
  }

  #followFrame(time) {
    const target = this.#followTarget();
    if (target === null || (this.#followY !== null && Math.abs(window.scrollY - this.#appliedY) > 1)) {
      this.#followY = null;
      this.#isFrameRequested = false;
      return;
    }
    const current = this.#followY ?? window.scrollY;
    const elapsed = Math.min(Math.max(time - this.#lastFrameTime, 0), 100);
    this.#lastFrameTime = time;
    const next = current + (target - current) * (1 - Math.exp(-elapsed / ScrollController.#FOLLOW_TIME_MS));
    const isDone = Math.abs(target - next) < 0.5;
    this.#followY = isDone ? null : next;
    this.currentScrollElement = null;
    window.scrollTo({ top: isDone ? target : next, behavior: "instant" });
    this.#appliedY = window.scrollY;
    if (isDone) {
      this.#isFrameRequested = false;
      return;
    }
    requestAnimationFrame(nextTime => this.#followFrame(nextTime));
  }

  static #FOLLOW_TIME_MS = 40;

  #followTarget() {
    const y = this.#sourceOffsetToY(this.#targetSourceOffset);
    if (y === null) {
      return null;
    }
    const root = document.scrollingElement;
    return Math.min(Math.max(y, 0), Math.max(root.scrollHeight - root.clientHeight, 0));
  }

  #sourceOffsetToY(offset) {
    let node = document.body.firstElementChild;
    const range = this.#rangeOf(node);
    if (!range) {
      return null;
    }
    let start = { offset: range.from, y: 0 };
    let end = { offset: range.to, y: document.documentElement.scrollHeight };
    while (node !== null) {
      const children = this.#positionedChildren(node, start, end);
      if (children.length === 0) {
        break;
      }
      if (getComputedStyle(children[0].element).display.startsWith("inline")) {
        [start, end] = ScrollController.#inlineSegment(children, offset, start, end);
        break;
      }
      let container = null;
      for (const child of children) {
        const box = child.element.getBoundingClientRect();
        const top = box.top + window.scrollY;
        const bottom = box.bottom + window.scrollY;
        if (offset < child.from) {
          end = { offset: child.from, y: top };
          break;
        }
        if (offset <= child.to) {
          container = child.element;
          if (child.from > start.offset) {
            start = { offset: child.from, y: top };
          }
          if (child.to < end.offset) {
            end = { offset: child.to, y: bottom };
          }
          break;
        }
        start = { offset: child.to, y: bottom };
      }
      node = container;
    }
    const length = end.offset - start.offset;
    const fraction = length > 0 ? Math.min(Math.max((offset - start.offset) / length, 0), 1) : 0;
    return start.y + fraction * Math.max(end.y - start.y, 0);
  }

  static #inlineSegment(children, offset, start, end) {
    let previous = start;
    for (const child of children) {
      const rects = child.element.getClientRects();
      if (rects.length === 0) {
        continue;
      }
      const top = rects[0].top + window.scrollY;
      if (child.from > offset) {
        return [previous, { offset: child.from, y: top }];
      }
      previous = { offset: child.from, y: top };
    }
    return [previous, end];
  }

  #positionedChildren(node, start, end) {
    const result = [];
    for (let child = node.firstElementChild; child !== null; child = child.nextElementSibling) {
      const range = this.#rangeOf(child);
      if (!range || range.to < start.offset || range.from > end.offset || child.getClientRects().length === 0) {
        continue;
      }
      result.push({ element: child, from: range.from, to: range.to });
    }
    return result;
  }

  #rangeOf(node) {
    const offsets = this.getNodeOffsets(node);
    if (!offsets) {
      return null;
    }
    return { from: Number(offsets[0]), to: Number(offsets[1]) };
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
