package com.intellij.xml;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 *         Date: 24.10.13
 */
public class HtmlXmlExtension extends DefaultXmlExtension {

  @Override
  public boolean isAvailable(PsiFile file) {
    return file.getFileType() == HtmlFileType.INSTANCE;
  }

  @Nullable
  @Override
  public String[][] getNamespacesFromDocument(XmlDocument parent, boolean declarationsExist) {
    return super.getNamespacesFromDocument(parent, false);
  }
}
