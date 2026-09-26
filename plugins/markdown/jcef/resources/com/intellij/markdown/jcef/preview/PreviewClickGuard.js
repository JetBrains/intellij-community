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
  const MAX_CONTROL_GROWTH = 4;
  const MAX_CONTROL_SLACK = 40;

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

  const isPlausibleTarget = (element, x, y) => {
    if (element === null || element === undefined) {
      return false;
    }
    const rect = element.getBoundingClientRect();
    return fitsViewport(rect) && pointInside(rect, x, y) && isVisiblySolid(element);
  };

  /**
   * Whether `element` covers enough of the page, out of the normal flow, to catch a click meant for the
   * document. An overlay has to be positioned to sit over content; a large image or a long line of text
   * in the flow does not, so size alone would flag those.
   */
  const coversPage = (element) => {
    const style = window.getComputedStyle(element);
    if (style.position !== 'fixed' && style.position !== 'absolute') {
      return false;
    }
    const rect = element.getBoundingClientRect();
    return rect.width > window.innerWidth * MAX_VIEWPORT_FRACTION &&
           rect.height > window.innerHeight * MAX_VIEWPORT_FRACTION;
  };

  /**
   * Reads no click coordinate, unlike the checks above: in JCEF `clientX`/`clientY` and
   * `elementFromPoint` do not reliably agree with layout, and disagreed by window position and
   * full-screen state (IJPL-247801). The browser already hit-tested the click, so the element it
   * dispatched to is the one to judge - only its own size and visibility are in question.
   */
  const isGenuineTarget = (event, element) =>
    isKeyboardActivation(event) ||
    (element !== null && element !== undefined && !coversPage(element) && isVisiblySolid(element));

  /**
   * An IDE control is laid out around the icon it contains, so a document that restyles it to catch
   * stray clicks has to make it far larger than that icon. The run anchor is legitimately zero-width
   * - `commandRunner.css` gives its image negative margins - so the icon, not the anchor, sets the
   * expected size.
   */
  const isProportionate = (container, icon) => {
    const outer = container.getBoundingClientRect();
    const inner = icon.getBoundingClientRect();
    return outer.width <= inner.width * MAX_CONTROL_GROWTH + MAX_CONTROL_SLACK &&
           outer.height <= inner.height * MAX_CONTROL_GROWTH + MAX_CONTROL_SLACK;
  };

  /**
   * For a control the IDE injects around an icon. Reads no click coordinate: in JCEF those disagree
   * with layout, which raised the confirmation on ordinary clicks (IJPL-247801).
   */
  const isGenuineControl = (event, container, icon) =>
    isKeyboardActivation(event) ||
    (container !== null && icon !== null && isVisiblySolid(icon) && isProportionate(container, icon));

  window.__IntelliJTools.clickGuard = {
    isKeyboardActivation: isKeyboardActivation,
    isPlausibleTarget: isPlausibleTarget,
    isGenuineTarget: isGenuineTarget,
    isGenuineControl: isGenuineControl,

    confirmationFlag: (needsConfirmation) => needsConfirmation ? "1" : "0",
  };
})();
