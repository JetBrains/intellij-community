package com.intellij.lang.properties.xml;

import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import icons.PropertiesIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 *         Date: 7/29/11
 */
public class XmlPropertiesIconProvider extends IconProvider {

  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    return element instanceof XmlFile && XmlPropertiesFile.getPropertiesFile((XmlFile)element) != null ? PropertiesIcons.XmlProperties : null;
  }
}
