// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

public class EmmetException extends Exception {
  public EmmetException() {
    super("Cannot expand emmet abbreviation: output is too big");
  }
}
