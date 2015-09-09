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
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 03.02.2003
 * Time: 18:29:13
 * To change this template use Options | File Templates.
 */
public class RootTagFilter extends PositionElementFilter{
  public RootTagFilter(ElementFilter filter){
    setFilter(filter);
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement scope){
    if (!(element instanceof XmlDocument)) return false;
    final XmlTag rootTag = ((XmlDocument)element).getRootTag();
    if(rootTag == null) return false;

    return getFilter().isAcceptable(rootTag, (PsiElement)element);
  }

  public String toString(){
    return "roottag(" + getFilter().toString() + ")";
  }
}
