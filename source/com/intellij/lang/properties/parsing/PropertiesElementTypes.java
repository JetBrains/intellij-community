package com.intellij.lang.properties.parsing;

import com.intellij.lang.Language;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2005
 * Time: 12:27:21 AM
 * To change this template use File | Settings | File Templates.
 */
public interface PropertiesElementTypes {
  PropertiesLanguage LANG = Language.findInstance(PropertiesLanguage.class);

  IFileElementType FILE = new IStubFileElementType(LANG);
  IStubElementType PROPERTY = new PropertyStubElementType();

  IStubElementType PROPERTIES_LIST = new PropertyListStubElementType();
  TokenSet PROPERTIES = TokenSet.create(PROPERTY);
}
