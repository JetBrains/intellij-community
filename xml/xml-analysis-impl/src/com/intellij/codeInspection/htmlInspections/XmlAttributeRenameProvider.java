// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.openapi.extensions.ExtensionPointName;

public interface XmlAttributeRenameProvider {
  ExtensionPointName<XmlAttributeRenameProvider> EP_NAME = ExtensionPointName.create("com.intellij.xml.xmlAttributeRenameProvider");

  RenameXmlAttributeFix[] getAttributeFixes(String name);
}
