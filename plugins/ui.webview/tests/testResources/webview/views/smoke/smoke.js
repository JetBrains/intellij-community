// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
window.__WEBVIEW_SMOKE_EXECUTED__ = true;
document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("smoke-root").textContent = "webview smoke ready";
  if (window.__WVI__ && typeof window.__WVI__.notification === "function") {
    void window.__WVI__.notification({ method: "webviewSmoke/ready" }).send({
      executed: true,
      text: document.getElementById("smoke-root").textContent,
      userAgent: navigator.userAgent,
    });
  }
});
