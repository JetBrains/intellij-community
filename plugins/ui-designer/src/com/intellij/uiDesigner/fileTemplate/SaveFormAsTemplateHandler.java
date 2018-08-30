// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.fileTemplate;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.actions.SaveFileAsTemplateHandler;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.PsiFile;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;
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
          Element element = JDOMUtil.load(fileText);
          Attribute attribute = element.getAttribute(UIFormXmlConstants.ATTRIBUTE_BIND_TO_CLASS);
          attribute.detach();
          return JDOMUtil.write(element, CodeStyle.getSettings(file).getLineSeparator());
        }
        catch (Exception ignored) {
        }
      }
    }
    return null;
  }
}
