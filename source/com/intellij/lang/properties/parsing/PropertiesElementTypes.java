package com.intellij.lang.properties.parsing;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.properties.parsing.PropertiesElementType;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2005
 * Time: 12:27:21 AM
 * To change this template use File | Settings | File Templates.
 */
public interface PropertiesElementTypes {
  IElementType FILE = new PropertiesElementType("FILE");
  IElementType PROPERTY = new PropertiesElementType("PROPERTY");

  TokenSet PROPERTIES = TokenSet.create(new IElementType[]{PROPERTY});
}
