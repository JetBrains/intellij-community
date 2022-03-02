// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xml.DomElementNavigationProvider;

public final class DomGotoActions {

  public static final ExtensionPointName<DomElementNavigationProvider>
    DOM_GOTO_SUPER = ExtensionPointName.create("com.intellij.dom.gotoSuper");

  private DomGotoActions() {
  }
}
