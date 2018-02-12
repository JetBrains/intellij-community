/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.packaging.setupPy

import com.intellij.ide.fileTemplates.DefaultCreateFromTemplateHandler
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.util.text.StringUtil

class PyCreateSetupPyFromTemplateHandler : DefaultCreateFromTemplateHandler() {

  override fun handlesTemplate(template: FileTemplate?): Boolean {
    return template == FileTemplateManager.getDefaultInstance().getInternalTemplate(CreateSetupPyAction.SETUP_SCRIPT_TEMPLATE_NAME)
  }

  override fun prepareProperties(props: MutableMap<String, Any>?) {
    if (props != null) {
      val description = props["Description"]
      if (description is String) {
        props["Description"] = StringUtil.escapeChar(description, '\'')
      }
    }
  }
}