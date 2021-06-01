// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlFile;


public interface XmlIdContributor {
  ExtensionPointName<XmlIdContributor> EP_NAME = ExtensionPointName.create("com.intellij.xml.idContributor");

  boolean suppressExistingIdValidation(XmlFile file);
}
