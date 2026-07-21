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
  // visibility, which CSS cannot fake, so a hijacked click is confirmed by the IDE or runs nothing.
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
      // A filter (opacity(), brightness(0), blur(), ...) can make the icon invisible while it stays
      // clickable and elementFromPoint still resolves to it - the opacity property above does not see this.
      if (style.filter && style.filter !== 'none') {
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

  const isClickableIcon = (icon, x, y) => {
    if (icon === null) {
      return false;
    }
    const rect = icon.getBoundingClientRect();
    return iconFitsViewport(rect) && pointInside(rect, x, y) && isVisiblySolid(icon);
  };

  const isGenuineButtonClick = (e, icon) => {
    // The cursor must hit the run <img> itself with nothing on top: elementFromPoint reports the <a> for a
    // pseudo-element and a foreign element for an overlay, so a disguised or covered button fails here.
    return window.document.elementFromPoint(e.clientX, e.clientY) === icon
      && isClickableIcon(icon, e.clientX, e.clientY);
  };

  const runIconAnchorBeneath = (x, y) => {
    for (const element of window.document.elementsFromPoint(x, y)) {
      const anchor = element.closest('a[data-command]');
      if (anchor !== null && isClickableIcon(anchor.querySelector('img'), x, y)) {
        return anchor;
      }
    }
    return null;
  };

  const resolveRunTarget = (e, target) => {
    // Keyboard activation has detail 0 and no coordinates; trust it (the CSP forbids forged events).
    if (e.detail === 0) {
      return { anchor: target, needsConfirmation: "0" };
    }
    const icon = target.querySelector('img');
    if (icon !== null) {
      return { anchor: target, needsConfirmation: isGenuineButtonClick(e, icon) ? "0" : "1" };
    }
    // No run icon on the clicked anchor (an author overlay such as #jump): run the real icon beneath the
    // cursor, always confirming because something is layered on top.
    const realAnchor = runIconAnchorBeneath(e.clientX, e.clientY);
    return realAnchor === null ? null : { anchor: realAnchor, needsConfirmation: "1" };
  };

  const postRunRequest = (e, anchor, needsConfirmation) => {
    const cmd = anchor.getAttribute('data-command');
    const cmdType = anchor.getAttribute('data-commandtype');
    const firstLineHash = anchor.getAttribute('data-firstLine');
    if (cmdType === 'block') {
      runBlock(cmd + ":" + firstLineHash + ":" + e.clientX + ":" + e.clientY + ":" + needsConfirmation);
    } else {
      runLine(cmd + ":" + needsConfirmation);
    }
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
      const resolved = resolveRunTarget(e, target);
      if (resolved !== null) {
        postRunRequest(e, resolved.anchor, resolved.needsConfirmation);
      }
      return false;
    }
  });
})();
