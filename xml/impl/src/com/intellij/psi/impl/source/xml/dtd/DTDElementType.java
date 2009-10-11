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
package com.intellij.psi.impl.source.xml.dtd;

import com.intellij.psi.tree.xml.IDTDElementType;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 18.08.2004
 * Time: 17:27:48
 * To change this template use File | Settings | File Templates.
 */
public interface DTDElementType {
  IDTDElementType DTD_DOCUMENT = new IDTDElementType("DTD_DOCUMENT");
  IDTDElementType DTD_ELEMENT_DECL = new IDTDElementType("DTD_ELEMENT_DECL");
  IDTDElementType DTD_ATTLIST_DECL = new IDTDElementType("DTD_ATTLIST_DECL");
  IDTDElementType DTD_NOTATION_DECL = new IDTDElementType("DTD_NOTATION_DECL");
  IDTDElementType DTD_ENTITY_DECL = new IDTDElementType("DTD_ENTITY_DECL");

  IDTDElementType DTD_ELEMENT_CONTENT_SPEC = new IDTDElementType("DTD_ELEMENT_CONTENT_SPEC");
  IDTDElementType DTD_GROUP = new IDTDElementType("DTD_GROUP");

  IDTDElementType DTD_ATTR_DECL = new IDTDElementType("DTD_ATTR_DECL");
  IDTDElementType DTD_ENUMERATION = new IDTDElementType("DTD_ENUMERATION");
  IDTDElementType DTD_NOTATION = new IDTDElementType("DTD_NOTATION");

  IDTDElementType DTD_ATTRIBUTE_VALUE = new IDTDElementType("DTD_ATTRIBUTE_VALUE");
  IDTDElementType DTD_NDATA = new IDTDElementType("DTD_NDATA");

  IDTDElementType DTD_ENTITY_REF = new IDTDElementType("DTD_ENTITY_REF");
  IDTDElementType DTD_ENUMERATED_TYPE = new IDTDElementType("DTD_ENUMERATED_TYPE");
  IDTDElementType DTD_PROCESSING_INSTRUCTION = new IDTDElementType("DTD_PROCESSING_INSTRUCTION");
  IDTDElementType DTD_CDATA = new IDTDElementType("DTD_CDATA");
  IDTDElementType DTD_DTD_DECL = new IDTDElementType("DTD_DTD_DECL");
  IDTDElementType DTD_WHITE_SPACE_HOLDER = new IDTDElementType("DTD_WHITE_SPACE_HOLDER");
  IDTDElementType DTD_EXTERNAL_ID = new IDTDElementType("DTD_EXTERNAL_ID");

  IDTDElementType DTD_COMMENT = new IDTDElementType("DTD_COMMENT");
}
