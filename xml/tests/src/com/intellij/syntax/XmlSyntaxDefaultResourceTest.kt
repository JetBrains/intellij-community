// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.syntax

import com.intellij.platform.syntax.scripts.assertPropertiesMatch
import com.intellij.xml.syntax.DefaultXmlSyntaxResourcesTestAccessor
import com.intellij.xml.syntax.XmlSyntaxBundle
import org.junit.jupiter.api.Test

class XmlSyntaxDefaultResourceTest {
  @Test
  fun testResourcesMatch() {
    assertPropertiesMatch(
      propertiesFileName = XmlSyntaxBundle.BUNDLE,
      defaultResourcesFileName = DefaultXmlSyntaxResourcesTestAccessor.defaultJavaSyntaxResourcesName,
      classLoader = XmlSyntaxBundle.javaClass.classLoader,
      actualMapping = DefaultXmlSyntaxResourcesTestAccessor.mappings,
    )
  }
}