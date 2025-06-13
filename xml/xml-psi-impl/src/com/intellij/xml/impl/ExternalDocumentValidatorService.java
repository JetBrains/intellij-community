// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.impl;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlDocument;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface ExternalDocumentValidatorService {
  static ExternalDocumentValidatorService getInstance() {
    return ApplicationManager.getApplication().getService(ExternalDocumentValidatorService.class);
  }

  void doValidation(final XmlDocument document, final Validator.ValidationHost host);
}
