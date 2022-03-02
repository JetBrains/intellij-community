// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
(function() {
  window.addEventListener("click", event => {
    const target = findButtonElement(event.target);
    if (target != null) {
      const encodedContent = target.getAttribute("data-fence-content");
      if (encodedContent != null) {
        const content = atob(encodedContent);
        console.log("Copied text:");
        console.log(content);
        window.__IntelliJTools.messagePipe.post("copy-button/copy", content);
      }
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
