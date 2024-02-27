// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceURLProvider

object HuggingFaceDocumentationPlaceholdersUtil {
  fun generateGatedEntityMarkdownString(entityId: String, entityKind: HuggingFaceEntityKind): String {
    val msg = "<font color='gray'>Sorry, this ${entityKind.printName} is gated.<br>" +
              "Please visit the [HuggingFace website](" +
              "${HuggingFaceURLProvider.getEntityCardLink(entityId, entityKind)}" +
              ")<br>for more details about the ${entityKind.printName} card.</font>"
    return msg
  }

  fun noReadmePlaceholder(entityId: String, entityKind: HuggingFaceEntityKind): String {
    val cardUrl = HuggingFaceURLProvider.getEntityCardLink(entityId, entityKind)
    return "This model has no description available. For more details, please follow the [link]($cardUrl)"
  }

  fun trimmedMdPlaceholder(entityId: String, entityKind: HuggingFaceEntityKind): String {
    val cardUrl = HuggingFaceURLProvider.getEntityCardLink(entityId, entityKind)
    return "*Please find the rest of the ${entityKind.printName} card [here]($cardUrl)*"
  }

  fun noInternetConnectionPlaceholder(entityId: String): String {
    return "Failed to fetch data for $entityId. Please check your internet connection."
  }
}
