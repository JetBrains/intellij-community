// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.markdown

import circlet.client.api.UnfurlAttachment
import circlet.client.api.UnfurlDetailsDateTime
import circlet.client.api.UnfurlDetailsProfile
import circlet.client.api.fullName
import circlet.platform.client.resolve
import com.intellij.openapi.util.text.HtmlChunk.link
import com.intellij.space.utils.formatPrettyDateTime
import platform.common.Unfurl

internal fun processUnfurls(markdown: String, unfurls: List<UnfurlAttachment>): String {
  var result = markdown
  unfurls.map { it.unfurl }.forEach { unfurl ->
    result = result.replace(unfurl.link, unfurl.createNewLink())
  }
  return result
}

private fun Unfurl.createNewLink(): String = when (val unfurlDetails = details) {
  is UnfurlDetailsProfile -> link(link, unfurlDetails.profile.resolve().name.fullName()).toString() // NON-NLS
  is UnfurlDetailsDateTime -> unfurlDetails.utcMilliseconds.formatPrettyDateTime()
  else -> link
}