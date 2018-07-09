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
package com.intellij.uiDesigner.fileTemplate;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.actions.SaveFileAsTemplateHandler;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.PsiFile;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.JdomKt;
import org.jdom.Attribute;
import org.jdom.Element;

/**
 * @author yole
 */
public class SaveFormAsTemplateHandler implements SaveFileAsTemplateHandler {
  @Override
  public String getTemplateText(final PsiFile file, final String fileText, final String nameWithoutExtension) {
    if (StdFileTypes.GUI_DESIGNER_FORM.equals(file.getFileType())) {
      LwRootContainer rootContainer = null;
      try {
        rootContainer = Utils.getRootContainer(fileText, null);
      }
      catch (Exception ignored) {
      }
      if (rootContainer != null && rootContainer.getClassToBind() != null) {
        try {
          Element document = JdomKt.loadElement(fileText);

          Attribute attribute = document.getAttribute(UIFormXmlConstants.ATTRIBUTE_BIND_TO_CLASS);
          attribute.detach();
          return JDOMUtil.write(document, CodeStyle.getSettings(file).getLineSeparator());
        }
        catch (Exception ignored) {
        }
      }
    }
    return null;
  }
}
