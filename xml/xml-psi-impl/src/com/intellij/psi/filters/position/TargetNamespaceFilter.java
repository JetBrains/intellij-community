/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.filters.position;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.XmlTextFilter;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ReflectionUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.05.2003
 * Time: 4:13:57
 * To change this template use Options | File Templates.
 */
public class TargetNamespaceFilter extends XmlTextFilter {
  public TargetNamespaceFilter(String str){
    super(str);
  }

  public TargetNamespaceFilter(String[] strs){
    super(strs);
  }

  @Override
  public boolean isClassAcceptable(Class hintClass){
    return ReflectionUtil.isAssignable(XmlTag.class, hintClass) || ReflectionUtil.isAssignable(XmlDocument.class, hintClass);
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof XmlTag){
      final String attributeValue = ((XmlTag)element).getAttributeValue("targetNamespace");
      if(attributeValue != null){
        for (String aMyValue : myValue) {
          if (aMyValue.equals(attributeValue)) return true;
        }
      }
    }
    else if(element instanceof XmlDocument){
      return isAcceptable(((XmlDocument) element).getRootTag(), context);
    }
    return false;
  }
}
