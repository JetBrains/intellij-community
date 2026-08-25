// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
(function() {
  // The button is an icon plus a tooltip, so a click on either is genuine.
  const guard = window.__IntelliJTools.clickGuard;

  window.addEventListener("click", event => {
    const target = findButtonElement(event.target);
    if (target != null) {
      const encodedContent = target.getAttribute("data-fence-content");
      if (encodedContent != null) {
        const needsConfirmation = !guard.isGenuineClickInside(event, target);
        window.__IntelliJTools.messagePipe.post(
          "copy-button/copy",
          guard.confirmationFlag(needsConfirmation) + ":" + encodedContent
        );
        if (needsConfirmation) {
          // The IDE is asking first, so nothing below is true yet.
          return;
        }
        console.log("Copied text:");
        console.log(encodedContent);
      }

      const tooltip = target.querySelector(".tooltiptext")
      tooltip.innerHTML = 'Copied!'
      setTimeout(function () {
        tooltip.innerHTML = 'Copy to clipboard'
      }, 1500)
    }
  });

  function findButtonElement(target) {
    while(target?.parentNode != null) {
      if (target?.classList?.contains("code-fence-highlighter-copy-button")) {
        return target;
      }
      target = target.parentNode;
    }
  }
})();
