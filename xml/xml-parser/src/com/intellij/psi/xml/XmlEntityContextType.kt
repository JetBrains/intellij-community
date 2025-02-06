// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.xml

enum class XmlEntityContextType {
  ELEMENT_CONTENT_SPEC,
  ATTRIBUTE_SPEC,
  ATTLIST_SPEC,
  ENTITY_DECL_CONTENT,
  GENERIC_XML,
  ENUMERATED_TYPE,
  ATTR_VALUE,

  ;
}
