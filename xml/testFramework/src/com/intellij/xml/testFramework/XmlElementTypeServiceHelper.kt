// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.testFramework

import com.intellij.html.embedding.BasicHtmlRawTextElementFactory
import com.intellij.lang.html.BackendHtmlElementFactory
import com.intellij.lang.html.BasicHtmlElementFactory
import com.intellij.lang.xml.BackendXmlElementFactory
import com.intellij.lang.xml.BasicXmlElementFactory
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable

object XmlElementTypeServiceHelper {
  @JvmStatic
  fun registerXmlElementTypeServices(
    application: MockApplication,
    testRootDisposable: Disposable,
  ) {
    application.registerService(
      BasicXmlElementFactory::class.java,
      BackendXmlElementFactory(),
      testRootDisposable,
    )

    application.registerService(
      BasicHtmlElementFactory::class.java,
      BackendHtmlElementFactory(),
      testRootDisposable,
    )

    application.registerService(
      BasicHtmlRawTextElementFactory::class.java,
      BackendHtmlElementFactory(),
      testRootDisposable,
    )
  }
}