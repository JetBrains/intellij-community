// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.testFramework

import com.intellij.html.embedding.BasicHtmlRawTextElementFactory
import com.intellij.lang.html.HtmlRawTextElementFactoryImpl
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable

object XmlElementTypeServiceHelper {
  @JvmStatic
  fun registerXmlElementTypeServices(
    application: MockApplication,
    testRootDisposable: Disposable,
  ) {
    application.registerService(
      BasicHtmlRawTextElementFactory::class.java,
      HtmlRawTextElementFactoryImpl(),
      testRootDisposable,
    )
  }
}