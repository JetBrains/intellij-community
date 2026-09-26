package com.jetbrains.python.documentation

import com.intellij.platform.workspace.jps.serialization.CustomImlComponentNameContributor
import com.jetbrains.python.documentation.PyDocumentationSettings.MODULE_STATE_COMPONENT

internal class PyDocumentationSettingsImlComponentNameContributor: CustomImlComponentNameContributor {
  override val componentName: String = MODULE_STATE_COMPONENT
}
