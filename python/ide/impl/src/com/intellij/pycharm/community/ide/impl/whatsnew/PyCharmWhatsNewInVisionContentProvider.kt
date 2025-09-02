// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.whatsnew

import com.intellij.platform.whatsNew.ContentSource
import com.intellij.platform.whatsNew.ResourceContentSource
import com.intellij.platform.whatsNew.WhatsNewInVisionContentProvider
import com.intellij.util.PlatformUtils.isCommunityEdition

class PyCharmWhatsNewInVisionContentProvider : WhatsNewInVisionContentProvider() {

  override fun getResourceNameByPath(path: String): String {
    return "whatsNew/$path"
  }

  override fun getResource(resourceName: String): ContentSource {
    return ResourceContentSource(PyCharmWhatsNewInVisionContentProvider::class.java.classLoader, resourceName)
  }

  override fun getResource(): ContentSource {
    // return a vision file for the current version
    val resourceName = if (isCommunityEdition()) "whatsNew/pycharmCE2025.2.json" else "whatsNew/pycharm2025.2.json"
    val resourceContentSource = ResourceContentSource(PyCharmWhatsNewInVisionContentProvider::class.java.classLoader, resourceName)
    return resourceContentSource
  }
}