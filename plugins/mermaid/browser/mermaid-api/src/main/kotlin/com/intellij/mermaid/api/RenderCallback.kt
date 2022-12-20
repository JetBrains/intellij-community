package com.intellij.mermaid.api

import org.w3c.dom.Element

// ((svgCode: string, bindFunctions?: ((element: Element) => void) | undefined) => void) | undefined
typealias RenderCallback = (svg: String, bind: ((((element: Element) -> Unit)?) -> Unit)?) -> Unit
