// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config;

import com.intellij.psi.tree.IFileElementType;

public interface BuildoutCfgElementTypes {
  IFileElementType FILE = new IFileElementType(BuildoutCfgLanguage.INSTANCE);
  BuildoutCfgElementType ANY_ELEMENT = new BuildoutCfgElementType("ANY");

  BuildoutCfgElementType SECTION = new BuildoutCfgElementType("SECTION");
  BuildoutCfgElementType SECTION_HEADER = new BuildoutCfgElementType("SECTION_HEADER");
  BuildoutCfgElementType OPTION = new BuildoutCfgElementType("OPTION");
  BuildoutCfgElementType KEY = new BuildoutCfgElementType("KEY");
  BuildoutCfgElementType VALUE = new BuildoutCfgElementType("VALUE");
  BuildoutCfgElementType VALUE_LINE = new BuildoutCfgElementType("VALUE_LINE");
}
