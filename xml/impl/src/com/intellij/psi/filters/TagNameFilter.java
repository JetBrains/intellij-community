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

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 13:57:35
 * To change this template use Options | File Templates.
 */
public class TagNameFilter extends PlainTextFilter {

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