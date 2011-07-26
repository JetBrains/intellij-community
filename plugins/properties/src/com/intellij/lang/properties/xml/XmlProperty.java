package com.intellij.lang.properties.xml;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 *         Date: 7/26/11
 */
public class XmlProperty implements IProperty {

  private final XmlTag myTag;

  public XmlProperty(XmlTag tag) {
    myTag = tag;
  }

  @Override
  public String getName() {
    return myTag.getAttributeValue("key");
  }

  @Override
  public PsiElement setName(String name) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String getKey() {
    return getName();
  }

  @Override
  public String getValue() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String getUnescapedValue() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String getUnescapedKey() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String getKeyValueSeparator() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void setValue(@NonNls @NotNull String value) throws IncorrectOperationException {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public PropertiesFile getPropertiesFile() throws PsiInvalidElementAccessException {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String getDocCommentText() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public PsiElement getPsiElement() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void navigate(boolean requestFocus) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public boolean canNavigate() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public boolean canNavigateToSource() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
