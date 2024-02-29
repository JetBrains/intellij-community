// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceURLProvider
import com.intellij.python.community.impl.huggingFace.service.PyHuggingFaceBundle

object HuggingFaceDocumentationPlaceholdersUtil {
  fun generateGatedEntityMarkdownString(entityId: String, entityKind: HuggingFaceEntityKind): String {
    val msg = PyHuggingFaceBundle.message(
      "python.hugging.face.placeholder.gated.model",
      entityKind.printName,
      HuggingFaceURLProvider.getEntityCardLink(entityId, entityKind),
      entityKind.printName)
    return msg
  }

  fun noReadmePlaceholder(entityId: String, entityKind: HuggingFaceEntityKind): String {
    val cardUrl = HuggingFaceURLProvider.getEntityCardLink(entityId, entityKind)
    val msg = PyHuggingFaceBundle.message("python.hugging.face.placeholder.no.readme", cardUrl)
    return msg
  }

  fun trimmedMdPlaceholder(entityId: String, entityKind: HuggingFaceEntityKind): String {
    val cardUrl = HuggingFaceURLProvider.getEntityCardLink(entityId, entityKind)
    val msg = PyHuggingFaceBundle.message("python.hugging.face.placeholder.trimmed", entityKind.printName, cardUrl)
    return msg
  }

  fun noInternetConnectionPlaceholder(entityId: String): String {
    val msg = PyHuggingFaceBundle.message("python.hugging.face.placeholder.no.internet", entityId)
    return msg
  }
}