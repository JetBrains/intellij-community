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
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.EvaluatedXmlName;

import java.util.List;

public interface AbstractCollectionChildDescription extends AbstractDomChildrenDescription {
  List<XmlTag> getSubTags(DomInvocationHandler handler, final XmlTag[] subTags, final XmlFile file);
  EvaluatedXmlName createEvaluatedXmlName(DomInvocationHandler parent, XmlTag childTag);
}
