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
    if (!hasHrefAttribute(link)) {
      return false;
    }
    const href = getHrefAttribute(link)
    if (href[0] === '#') {
      const elementId = href.toLowerCase().substring(1);
      const decodedElementId = decodeURI(elementId)
      const elementById = window.document.getElementById(decodedElementId);
      if (elementById) {
        elementById.scrollIntoViewIfNeeded();
      }
    }
    else {
      openInExternalBrowser(href);
    }
    return false;
  };

  window.document.onclick = function(e) {
    let target = e.target;
    while (target && target.tagName.toLowerCase() !== 'a') {
      target = target.parentNode;
    }
    if (!target) {
      return true;
    }
    if (target.tagName.toLowerCase() === 'a' && hasHrefAttribute(target)) {
      e.stopPropagation();
      return window.__IntelliJTools.processClick(target);
    }
  };

  const hasHrefAttribute = (target) => target.hasAttribute('href') || target.hasAttribute('xlink:href');
  const getHrefAttribute = (target) => target.getAttribute('href') || target.getAttribute('xlink:href');

})();
