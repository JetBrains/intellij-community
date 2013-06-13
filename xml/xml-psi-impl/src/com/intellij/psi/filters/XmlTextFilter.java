/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
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
public class XmlTextFilter implements ElementFilter, InitializableFilter{
  protected String[] myValue;
  private boolean myCaseInsensitiveFlag = false;

  public XmlTextFilter(){
    myValue = ArrayUtil.EMPTY_STRING_ARRAY;
  }
  public XmlTextFilter(@NonNls String value, boolean incensetiveFlag){
    myCaseInsensitiveFlag = incensetiveFlag;
    myValue = new String[1];
    myValue[0] = value;
  }

  public XmlTextFilter(@NonNls String value){
    myValue = new String[1];
    myValue[0] = value;
  }

  public XmlTextFilter(@NonNls String... values){
    myValue = values;
  }

  public XmlTextFilter(@NonNls String value1, @NonNls String value2){
    myValue = new String[2];
    myValue[0] = value1;
    myValue[1] = value2;
  }

  public boolean isClassAcceptable(Class hintClass){
    return true;
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element != null) {
      for (final String value : myValue) {
        if (value == null) {
          return true;
        }
        final String elementText = getTextByElement(element);
        if (myCaseInsensitiveFlag) {
          if (value.equalsIgnoreCase(elementText)) return true;
        }
        else {
          if (value.equals(elementText)) return true;
        }
      }
    }

    return false;
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
      if (element instanceof XmlTag) {
        elementValue = ((XmlTag)element).getLocalName();
      } else {
        elementValue = ((PsiNamedElement)element).getName();
      }
    }
    else if (element instanceof PsiElement) {
      elementValue = ((PsiElement) element).getText();
    }
    return elementValue;
  }
}
