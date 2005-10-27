package com.intellij.lang.properties.parsing;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.lang.Language;
import com.intellij.lang.properties.PropertiesLanguage;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2005
 * Time: 12:27:21 AM
 * To change this template use File | Settings | File Templates.
 */
public interface PropertiesElementTypes {
  IFileElementType FILE = new IFileElementType(Language.findInstance(PropertiesLanguage.class));
  IElementType PROPERTY = new PropertiesElementType("PROPERTY");
  IElementType PROPERTIES_LIST = new PropertiesElementType("PROPERTIES_LIST");
  TokenSet PROPERTIES = TokenSet.create(PROPERTY);
}
