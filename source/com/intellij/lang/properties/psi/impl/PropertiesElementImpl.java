package com.intellij.lang.properties.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.properties.PropertiesFileType;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 30, 2005
 * Time: 8:23:10 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesElementImpl extends ASTWrapperPsiElement  {
  public PropertiesElementImpl(final ASTNode node) {
    super(node);
  }

  @NotNull
  public Language getLanguage() {
    return PropertiesFileType.FILE_TYPE.getLanguage();
  }
}
