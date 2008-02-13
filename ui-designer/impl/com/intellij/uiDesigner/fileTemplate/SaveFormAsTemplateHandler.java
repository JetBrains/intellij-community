package com.intellij.uiDesigner.fileTemplate;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.ide.actions.SaveFileAsTemplateHandler;
import org.jdom.Attribute;
import org.jdom.Document;

import java.io.CharArrayWriter;

/**
 * @author yole
 */
public class SaveFormAsTemplateHandler implements SaveFileAsTemplateHandler {
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
          Document document = JDOMUtil.loadDocument(fileText);

          Attribute attribute = document.getRootElement().getAttribute(UIFormXmlConstants.ATTRIBUTE_BIND_TO_CLASS);
          attribute.detach();

          CharArrayWriter writer = new CharArrayWriter();
          JDOMUtil.writeDocument(document, writer, CodeStyleSettingsManager.getSettings(file.getProject()).getLineSeparator());

          return writer.toString();
        }
        catch (Exception ignored) {
        }
      }
    }
    return null;
  }
}
