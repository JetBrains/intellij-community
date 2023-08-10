package com.intellij.mermaid.api

import org.w3c.dom.Element

external interface MermaidRenderResult {
  /**
   * The svg code for the rendered graph.
   */
  var svg: String

  /**
   * Bind function to be called after the svg has been inserted into the DOM.
   * This is necessary for adding event listeners to the elements in the svg.
   * ```js
   * const { svg, bindFunctions } = mermaidAPI.render('id1', 'graph TD;A-->B');
   * div.innerHTML = svg;
   * bindFunctions?.(div); // To call bindFunctions only if it's present.
   * ```
   */
  @JsName("bindFunctions")
  val bindCallback: ((Element) -> Unit)?
}

fun MermaidRenderResult.appendTo(element: Element) {
  element.innerHTML = svg
  bindCallback?.invoke(element)
}
