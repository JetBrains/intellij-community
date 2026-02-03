package com.intellij.lang.properties

import com.intellij.lang.properties.psi.Property
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import java.util.stream.Stream

@ApiStatus.Internal
interface PropertiesUtilService {
  fun getResourceBundle(file: PropertiesFileImpl): ResourceBundle
  fun shouldReadIndex(): Boolean
  fun getProperties(key: String, project: Project, scope: GlobalSearchScope): Stream<Property>

  class Empty: PropertiesUtilService {
    override fun getResourceBundle(file: PropertiesFileImpl): ResourceBundle {
      throw UnsupportedOperationException()
    }

    override fun shouldReadIndex(): Boolean {
      return false
    }

    override fun getProperties(key: String, project: Project, scope: GlobalSearchScope): Stream<Property> {
      throw UnsupportedOperationException()
    }
  }
}