package com.intellij.xml

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
  }
}