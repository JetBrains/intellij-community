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
      const cmd = target.getAttribute('data-command')
      let cmdType = target.getAttribute('data-commandtype')
      if (cmdType === 'block') {
        runBlock(cmd);
      } else {
        runLine(cmd);
      }
      return false;
    }
  });
})();
