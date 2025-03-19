// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.whatsnew

import com.intellij.platform.whatsNew.ContentSource
import com.intellij.platform.whatsNew.ResourceContentSource
import com.intellij.platform.whatsNew.WhatsNewInVisionContentProvider
import com.intellij.util.PlatformUtils.isCommunityEdition

class PyCharmWhatsNewInVisionContentProvider : WhatsNewInVisionContentProvider() {

  override fun getResource(): ContentSource {
    // return a vision file for the current version
    val resourceName = if (isCommunityEdition()) "whatsNew/pycharmCE2024.2.json" else "whatsNew/pycharm2024.2.json"
    val resourceContentSource = ResourceContentSource(PyCharmWhatsNewInVisionContentProvider::class.java.classLoader, resourceName)
    return resourceContentSource
  }
}