// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties

import com.intellij.lang.properties.psi.Property
import com.intellij.lang.properties.psi.PropertyKeyIndex
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import java.util.stream.Stream

class PropertiesUtilServiceImpl: PropertiesUtilService {
  override fun getResourceBundle(file: PropertiesFileImpl): ResourceBundle {
    return PropertiesImplUtil.getResourceBundle(file)
  }

  override fun shouldReadIndex(): Boolean {
    return true
  }

  override fun getProperties(key: String, project: Project, scope: GlobalSearchScope): Stream<Property> {
    return PropertyKeyIndex.getInstance().getProperties(key, project, scope).stream()
  }
}