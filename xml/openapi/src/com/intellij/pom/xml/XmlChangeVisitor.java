/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.pom.xml;

import com.intellij.pom.xml.events.*;

/**
 * @author peter
 */
public interface XmlChangeVisitor {
  void visitXmlAttributeSet(final XmlAttributeSet xmlAttributeSet);

  void visitDocumentChanged(final XmlDocumentChanged xmlDocumentChanged);

  void visitXmlElementChanged(final XmlElementChanged xmlElementChanged);

  void visitXmlTagChildAdd(final XmlTagChildAdd xmlTagChildAdd);

  void visitXmlTagChildChanged(final XmlTagChildChanged xmlTagChildChanged);

  void visitXmlTagChildRemoved(final XmlTagChildRemoved xmlTagChildRemoved);

  void visitXmlTagNameChanged(final XmlTagNameChanged xmlTagNameChanged);

  void visitXmlTextChanged(final XmlTextChanged xmlTextChanged);
}
