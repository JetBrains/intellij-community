package com.intellij.lang.properties.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class PropertiesElementImpl extends ASTWrapperPsiElement  {
  public PropertiesElementImpl(final ASTNode node) {
    super(node);
  }

  @NotNull
  public Language getLanguage() {
    return StdFileTypes.PROPERTIES.getLanguage();
  }
}
