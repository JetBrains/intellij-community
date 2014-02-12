package com.intellij.ide.browsers.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.List;

public abstract class OpenInBrowserActionProducer {
  static final ExtensionPointName<OpenInBrowserActionProducer> EP_NAME = ExtensionPointName.create("org.jetbrains.openInBrowserAction");

  public abstract List<AnAction> getActions();
}