// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
if (window.__IntelliJTools === undefined) {
  window.__IntelliJTools = {}
}

(function() {
  const runLine = (cmd) => {
    try {
      window.__IntelliJTools.messagePipe.post("runLine", cmd);
    }
    finally {}
  };

  const runBlock = (cmd) => {
    try {
      window.__IntelliJTools.messagePipe.post("runBlock", cmd);
    }
    finally {}
  };

  // Clickjacking guard. Author CSS shares this document and can hide, move, resize, or overlay the run
  // button so a click meant for elsewhere triggers a command. These checks read live geometry and
  // visibility, which CSS cannot fake. A failing click is not dropped - it is forwarded with a flag so the
  // IDE confirms the exact command first; a hijacked click can never run silently.
  const isVisiblySolid = (el) => {
    let node = el;
    while (node) {
      const style = window.getComputedStyle(node);
      if (style.visibility === 'hidden' || style.visibility === 'collapse') {
        return false;
      }
      const opacity = parseFloat(style.opacity);
      if (!Number.isNaN(opacity) && opacity < 0.9) {
        return false;
      }
      node = node.parentElement;
    }
    return true;
  };

  const iconFitsViewport = (rect) =>
    rect.width > 0 && rect.height > 0 &&
    rect.width <= window.innerWidth * 0.5 && rect.height <= window.innerHeight * 0.5;

  const pointInside = (rect, x, y) =>
    x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;

  const isGenuineButtonClick = (e, target) => {
    // Keyboard activation has detail 0 and no coordinates; trust it (the CSP forbids forged events).
    if (e.detail === 0) {
      return true;
    }
    // Anchor on the run <img>, not the <a>: a disguised button hides the icon and fakes a label, leaving
    // the <a> clickable but the click no longer landing on the icon.
    const icon = target.querySelector('img');
    if (icon === null) {
      return false;
    }
    const rect = icon.getBoundingClientRect();
    const x = e.clientX;
    const y = e.clientY;
    // The cursor must hit the icon itself - elementFromPoint reports the <a> for a pseudo-element and a
    // foreign element for an overlay on top - and the icon must be visibly solid.
    return iconFitsViewport(rect)
      && pointInside(rect, x, y)
      && window.document.elementFromPoint(x, y) === icon
      && isVisiblySolid(icon);
  };

  window.document.addEventListener("click", function(e) {
    let target = e.target;
    while (target && target.tagName !== 'A') {
      target = target.parentNode;
    }
    if (!target) {
      return true;
    }
    if (target.tagName === 'A' && target.hasAttribute("data-command")) {
      e.stopPropagation();
      e.preventDefault();
      const needsConfirmation = isGenuineButtonClick(e, target) ? "0" : "1";
      const cmd = target.getAttribute('data-command')
      let cmdType = target.getAttribute('data-commandtype')
      let firstLineHash = target.getAttribute('data-firstLine')
      if (cmdType === 'block') {
        runBlock(cmd + ":" + firstLineHash + ":" + e.clientX + ":" + e.clientY + ":" + needsConfirmation);
      } else {
        runLine(cmd + ":" + needsConfirmation);
      }
      return false;
    }
  });
})();
