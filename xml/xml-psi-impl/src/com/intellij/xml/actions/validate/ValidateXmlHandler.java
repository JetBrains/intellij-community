// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.actions.validate;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlFile;

public interface ValidateXmlHandler {
  ExtensionPointName<ValidateXmlHandler> EP_NAME = ExtensionPointName.create("com.intellij.xml.validateHandler");

  void doValidate(XmlFile file);
  boolean isAvailable(XmlFile file);
}
