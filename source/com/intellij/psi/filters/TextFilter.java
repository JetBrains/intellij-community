package com.intellij.psi.filters;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiType;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;

import java.util.StringTokenizer;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 13:57:35
 * To change this template use Options | File Templates.
 */
public class TextFilter
 implements ElementFilter, InitializableFilter{
  protected String[] myValue;
  private boolean myCaseInsensitiveFlag = false;

  public TextFilter(){
    myValue = ArrayUtil.EMPTY_STRING_ARRAY;
  }
  public TextFilter(String value, boolean incensetiveFlag){
    myCaseInsensitiveFlag = incensetiveFlag;
    myValue = new String[1];
    myValue[0] = value;
  }

  public TextFilter(String value){
    myValue = new String[1];
    myValue[0] = value;
  }

  public TextFilter(String[] values){
    myValue = values;
  }

  public TextFilter(String value1, String value2){
    myValue = new String[2];
    myValue[0] = value1;
    myValue[1] = value2;
  }

  public boolean isClassAcceptable(Class hintClass){
    return true;
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element != null) {
      for(int i = 0; i < myValue.length; i++){
        final String value = myValue[i];
        if(value == null)
          return true;
        final String elementText = getTextByElement(element);
        if(myCaseInsensitiveFlag){
          if(value.equalsIgnoreCase(elementText)) return true;
        }
        else{
          if(value.equals(elementText)) return true;
        }
      }
    }

    return false;
  }

  public void readExternal(Element element)
    throws InvalidDataException{
    final StringTokenizer tok = new StringTokenizer(element.getTextTrim(), "|");
    int i = 0;

    myValue = new String[tok.countTokens()];
    while(tok.hasMoreTokens()){
      myValue[i++] = tok.nextToken().trim();
    }
  }

  public void writeExternal(Element element)
    throws WriteExternalException{
    throw new WriteExternalException("Filter data could _not_ be written");
  }

  public String toString(){
    String ret = "(";
    for(int i = 0; i < myValue.length; i++){
      ret += myValue[i];
      if(i < myValue.length - 1){
        ret += " | ";
      }
    }
    ret += ")";
    return ret;
  }

  public void init(Object[] fromGetter){
    try{
      myValue = new String[fromGetter.length];
      System.arraycopy(fromGetter, 0, myValue, 0, fromGetter.length);
    }
    catch(ClassCastException cce){
      myValue = ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }

  protected String getTextByElement(Object element){
    String elementValue = null;
    if(element instanceof PsiNamedElement){
      elementValue = ((PsiNamedElement)element).getName();
    }
    else if (element instanceof PsiType) {
      elementValue = ((PsiType) element).getPresentableText();
    }
    else if (element instanceof PsiElement) {
      elementValue = ((PsiElement) element).getText();
    }
    return elementValue;
  }
}
