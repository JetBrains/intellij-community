package com.intellij.psi.filters;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 13:57:35
 * To change this template use Options | File Templates.
 */
public class TagNameFilter extends PlainTextFilter {

  public TagNameFilter(){
    myValue = ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public TagNameFilter(@NonNls String value, boolean insensitiveFlag) {
    super(value, insensitiveFlag);
  }

  public TagNameFilter(@NonNls String value){
    super(value);
  }

  public TagNameFilter(@NonNls String... values){
    super(values);
  }

  public TagNameFilter(@NonNls String value1, @NonNls String value2){
    super(value1, value2);
  }

  @Override
  protected String getTextByElement(final Object element) {
    if (element instanceof XmlTag) {
      return ((XmlTag)element).getLocalName();
    }
    else {
      return super.getTextByElement(element);
    }
  }
}