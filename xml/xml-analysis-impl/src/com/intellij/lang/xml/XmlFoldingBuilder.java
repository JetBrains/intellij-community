// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.xml;

import com.intellij.application.options.editor.XmlFoldingSettings;
import com.intellij.lang.XmlCodeFoldingBuilder;
import com.intellij.lang.XmlCodeFoldingSettings;

/**
 * @author Maxim.Mossienko
 */
public class XmlFoldingBuilder extends XmlCodeFoldingBuilder {
  @Override
  protected XmlCodeFoldingSettings getFoldingSettings() {
    return XmlFoldingSettings.getInstance();
  }
}
