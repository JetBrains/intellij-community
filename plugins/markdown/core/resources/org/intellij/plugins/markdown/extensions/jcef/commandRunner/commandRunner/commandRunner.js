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

  // The costliest thing a hijacked click reaches, so use the strictest check.
  const guard = window.__IntelliJTools.clickGuard;

  const runIconAnchorBeneath = (x, y) => {
    for (const element of window.document.elementsFromPoint(x, y)) {
      const anchor = element.closest('a[data-command]');
      if (anchor !== null && guard.isPlausibleTarget(anchor.querySelector('img'), x, y)) {
        return anchor;
      }
    }
    return null;
  };

  const resolveRunTarget = (e, target) => {
    if (guard.isKeyboardActivation(e)) {
      return { anchor: target, needsConfirmation: false };
    }
    const icon = target.querySelector('img');
    if (icon !== null) {
      return { anchor: target, needsConfirmation: !guard.isGenuineClickOn(e, icon) };
    }
    // No run icon on the clicked anchor (an author overlay such as #jump): run the real icon beneath the
    // cursor, always confirming because something is layered on top.
    const realAnchor = runIconAnchorBeneath(e.clientX, e.clientY);
    return realAnchor === null ? null : { anchor: realAnchor, needsConfirmation: true };
  };

  const postRunRequest = (e, anchor, needsConfirmation) => {
    const cmd = anchor.getAttribute('data-command');
    const cmdType = anchor.getAttribute('data-commandtype');
    const firstLineHash = anchor.getAttribute('data-firstLine');
    const flag = guard.confirmationFlag(needsConfirmation);
    if (cmdType === 'block') {
      runBlock(cmd + ":" + firstLineHash + ":" + e.clientX + ":" + e.clientY + ":" + flag);
    } else {
      runLine(cmd + ":" + e.clientX + ":" + e.clientY + ":" + flag);
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
