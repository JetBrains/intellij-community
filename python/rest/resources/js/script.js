// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
window.__IntelliJTools = {};
window.onclick = e => {
  if (e.target.tagName !== "A") return true;

  e.stopPropagation();
  e.preventDefault();

  const href = e.target.href;
  if (href.indexOf("#") !== -1 && !/^https?:\/\//i.test(href)) { // internal link
    const elementId = href.split('#')[1];
    const elementById = window.document.getElementById(elementId);
    if (elementById) {
      elementById.scrollIntoViewIfNeeded();
    }
    return;
  }
  if (window.__IntelliJTools.openInBrowserCallback !== undefined) {
    window.__IntelliJTools.openInBrowserCallback(href);
  }
};