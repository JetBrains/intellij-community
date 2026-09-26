// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceURLProvider
import com.intellij.python.community.impl.huggingFace.service.PyHuggingFaceBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
object HuggingFaceDocumentationPlaceholdersUtil {
  @Nls
  fun generateGatedEntityMarkdownString(entityId: String, entityKind: HuggingFaceEntityKind): String {
    val msg = PyHuggingFaceBundle.message(
      "python.hugging.face.placeholder.gated.model",
      entityKind.printName,
      HuggingFaceURLProvider.getEntityCardLink(entityId, entityKind),
      entityKind.printName)
    return msg
  }

  @Nls
  fun noReadmePlaceholder(entityId: String, entityKind: HuggingFaceEntityKind): String {
    // todo: FUS it
    val cardUrl = HuggingFaceURLProvider.getEntityCardLink(entityId, entityKind)
    val msg = PyHuggingFaceBundle.message("python.hugging.face.placeholder.no.readme", cardUrl)
    return msg
  }

  @Nls
  fun noInternetConnectionPlaceholder(entityId: String): String =
    // todo: FUS it
    PyHuggingFaceBundle.message("python.hugging.face.placeholder.no.internet", entityId)

  @Nls
  fun notFoundErrorPlaceholder(entityId: String): String =
    // todo: FUS it
    PyHuggingFaceBundle.message("python.hugging.face.placeholder.not.found", entityId)

}