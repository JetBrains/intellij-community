// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.impl;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.psi.xml.XmlDocument;

public class ExternalDocumentValidatorServiceImpl implements ExternalDocumentValidatorService {
  @Override
  public void doValidation(XmlDocument document, Validator.ValidationHost host) {
    ExternalDocumentValidator.doValidation(document, host);
  }
}
