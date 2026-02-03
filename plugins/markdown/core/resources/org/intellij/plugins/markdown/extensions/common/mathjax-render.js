IncrementalDOM.notifications.afterPatchListeners.push(() => {
  document.querySelectorAll("span.math").forEach(function (value) {
    renderTexElement(value);
  });
});

function renderTexElement(element) {
  const value = element.textContent.trim();
  MathJax.texReset();
  let isInline = element.getAttribute("inline") === "true";
  MathJax.tex2svgPromise(value, {display: !isInline}).then(function (node) {
    element.innerHTML = '';
    element.appendChild(node);
    MathJax.startup.document.clear();
    MathJax.startup.document.updateDocument();
  });
};