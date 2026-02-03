// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.PropertyKey;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public interface UpdaterUI {
  @Target(ElementType.TYPE_USE)
  @Nls(capitalization = Nls.Capitalization.Title)
  @interface Title { }

  @Target(ElementType.TYPE_USE)
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @interface Message { }

  void setDescription(@Message String text);

  void startProcess(@Title String title);
  void setProgress(int percentage);
  void setProgressIndeterminate();
  void checkCancelled() throws OperationCancelledException;

  void showError(@Message String message);

  Map<String, ValidationResult.Option> askUser(List<ValidationResult> validationResults) throws OperationCancelledException;

  default @Nls String bold(@Nls String text) { return text; }

  String BUNDLE = "messages.UpdaterBundle";

  static @Nls String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... parameters) {
    var bundle = ResourceBundle.getBundle(BUNDLE);
    var template = bundle.getString(key);
    return MessageFormat.format(template, parameters);
  }
}
