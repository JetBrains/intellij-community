// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.formatter;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jetbrains.yaml.YAMLLanguage;

public class YAMLCodeStyleSettings extends CustomCodeStyleSettings {
  public int ALIGN_VALUES_PROPERTIES = DO_NOT_ALIGN;

  public static final int DO_NOT_ALIGN = 0;
  public static final int ALIGN_ON_VALUE = 1;
  public static final int ALIGN_ON_COLON = 2;

  public boolean INDENT_SEQUENCE_VALUE = false;

  public boolean SEQUENCE_ON_NEW_LINE = false;
  public boolean BLOCK_MAPPING_ON_NEW_LINE = false;

  public YAMLCodeStyleSettings(CodeStyleSettings container) {
    super(YAMLLanguage.INSTANCE.getID(), container);
  }
}
