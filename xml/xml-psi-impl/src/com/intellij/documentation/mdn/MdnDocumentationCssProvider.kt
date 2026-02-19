// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.documentation.mdn

import com.intellij.codeInsight.documentation.DocumentationCssProvider
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil.spaceAfterParagraph
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil.spaceBeforeParagraph
import java.util.function.Function

class MdnDocumentationCssProvider : DocumentationCssProvider {
  override fun generateCss(scaleFunction: Function<Int, Int>, isInlineEditorContext: Boolean): String? {
    val beforeSpacing = scaleFunction.apply(spaceBeforeParagraph)
    val afterSpacing = scaleFunction.apply(spaceAfterParagraph)
    return """
        details.mdn-baseline, div.mdn-baseline {
            margin-bottom: ${afterSpacing * 2}px;
        }
        
        table.mdn-baseline {
            border-spacing: 0; 
            border-width: 0;
        }
        
        td.mdn-baseline-icon {
            padding: 0 ${beforeSpacing}px 0 0
        }
        
        td.mdn-baseline-info {
            padding: 0; 
            width: 100%;
        }
        
        details.mdn-baseline p {
            margin: 0;
            line-height: 100%;
        }
        
        .mdn-bottom-margin {
            margin-bottom: ${afterSpacing * 2}px
        }
    """.trimIndent()
  }
}