// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
if (window.__IntelliJTools === undefined) {
  window.__IntelliJTools = {}
}

/*
 * A document's CSS shares this page with the controls the IDE injects into it, so a click can land
 * somewhere the reader never intended. Nothing here can prove intent; these checks read live geometry
 * and visibility, which CSS cannot fake, and only report. Acting on the answer is the IDE's job.
 */
(function() {
  const MAX_VIEWPORT_FRACTION = 0.5;
  const MIN_OPACITY = 0.9;

  const isVisiblySolid = (element) => {
    let node = element;
    while (node) {
      const style = window.getComputedStyle(node);
      if (style.visibility === 'hidden' || style.visibility === 'collapse') {
        return false;
      }
      const opacity = parseFloat(style.opacity);
      if (!Number.isNaN(opacity) && opacity < MIN_OPACITY) {
        return false;
      }
      // A filter can hide an element while it stays clickable; the opacity check above does not see that.
      if (style.filter && style.filter !== 'none') {
        return false;
      }
      node = node.parentElement;
    }
    return true;
  };

  const fitsViewport = (rect) =>
    rect.width > 0 && rect.height > 0 &&
    rect.width <= window.innerWidth * MAX_VIEWPORT_FRACTION &&
    rect.height <= window.innerHeight * MAX_VIEWPORT_FRACTION;

  const pointInside = (rect, x, y) =>
    x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;

  // detail 0, no coordinates: CSS cannot aim it, and the CSP forbids forged events.
  const isKeyboardActivation = (event) => event.detail === 0;

  const topElementAt = (event) => window.document.elementFromPoint(event.clientX, event.clientY);

  const isPlausibleTarget = (element, x, y) => {
    if (element === null || element === undefined) {
      return false;
    }
    const rect = element.getBoundingClientRect();
    return fitsViewport(rect) && pointInside(rect, x, y) && isVisiblySolid(element);
  };

  /** elementFromPoint reports a pseudo-element's host, and a foreign element for an overlay. */
  const isGenuineClickOn = (event, element) =>
    isKeyboardActivation(event) ||
    (topElementAt(event) === element && isPlausibleTarget(element, event.clientX, event.clientY));

  /** Looser: a control's own icon or tooltip must not read as an overlay over it. */
  const isGenuineClickInside = (event, container) => {
    if (isKeyboardActivation(event)) {
      return true;
    }
    const top = topElementAt(event);
    return top !== null && container.contains(top) &&
           isPlausibleTarget(container, event.clientX, event.clientY);
  };

  window.__IntelliJTools.clickGuard = {
    isKeyboardActivation: isKeyboardActivation,
    isPlausibleTarget: isPlausibleTarget,
    isGenuineClickOn: isGenuineClickOn,
    isGenuineClickInside: isGenuineClickInside,

    confirmationFlag: (needsConfirmation) => needsConfirmation ? "1" : "0",
  };
})();
