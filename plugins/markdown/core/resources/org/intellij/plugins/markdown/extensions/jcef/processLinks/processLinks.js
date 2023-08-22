// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
if (window.__IntelliJTools === undefined) {
  window.__IntelliJTools = {}
}

(function() {
  const openInExternalBrowser = (href) => {
    try {
      window.__IntelliJTools.messagePipe.post("openLink", href);
    }
    finally {}
  };

  window.__IntelliJTools.processClick = function(link) {
    if (!link.hasAttribute('href')) {
      return false;
    }
    const href = link.getAttribute('href')
    if (href[0] === '#') {
      const elementId = href.substring(1);
      const elementById = window.document.getElementById(elementId);
      if (elementById) {
        elementById.scrollIntoViewIfNeeded();
      }
    }
    else {
      openInExternalBrowser(link.href);
    }
    return false;
  };

  window.document.onclick = function(e) {
    let target = e.target;
    while (target && target.tagName !== 'A') {
      target = target.parentNode;
    }
    if (!target) {
      return true;
    }
    if (target.tagName === 'A' && target.hasAttribute('href')) {
      e.stopPropagation();
      return window.__IntelliJTools.processClick(target);
    }
  };
})();
