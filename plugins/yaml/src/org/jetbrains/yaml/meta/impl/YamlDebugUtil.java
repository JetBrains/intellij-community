// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.meta.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class YamlDebugUtil {
  @NotNull
  static String getDebugInfo(@Nullable PsiElement psi) {
    if (psi == null) {
      return "<null>";
    }

    String text = psi.getText();
    if (text.contains("\n")) {
      int firstEol = text.indexOf('\n');
      int lastEol = text.lastIndexOf('\n');
      if (firstEol >= 0) {
        text = text.substring(0, firstEol) + " ... " + text.substring(lastEol + 1);
      }
    }

    return psi + ", range: " + psi.getTextRange() + ", text: `" + text + "`";
  }
}
