// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config;

public interface BuildoutCfgTokenTypes {
  BuildoutCfgElementType COMMENT = new BuildoutCfgElementType("COMMENT");

  BuildoutCfgElementType TEXT = new BuildoutCfgElementType("TEXT");

  BuildoutCfgElementType SECTION_NAME = new BuildoutCfgElementType("SECTION_NAME");

  BuildoutCfgElementType WHITESPACE = new BuildoutCfgElementType("WHITESPACE");

  BuildoutCfgElementType KEY_CHARACTERS = new BuildoutCfgElementType("KEY_CHARACTERS");
  BuildoutCfgElementType KEY_VALUE_SEPARATOR = new BuildoutCfgElementType("KEY_VALUE_SEPARATOR");
  BuildoutCfgElementType VALUE_CHARACTERS = new BuildoutCfgElementType("VALUE_CHARACTERS");

  BuildoutCfgElementType LBRACKET = new BuildoutCfgElementType("[");
  BuildoutCfgElementType RBRACKET = new BuildoutCfgElementType("]");

  BuildoutCfgElementType ERROR = new BuildoutCfgElementType("ERROR");
}

