// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.navigation;

import com.intellij.lang.Language;
import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.YAMLKeyValue;

public class YAMLScalarKeyDeclarationSearcher extends PomDeclarationSearcher {
  public void findDeclarationsAt(@NotNull PsiElement element, int offsetInElement, Consumer<PomTarget> consumer) {
    final Language language = element.getLanguage();
    if (language == YAMLLanguage.INSTANCE) {
      final YAMLKeyValue kv = PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class);
      if (kv != null) {
        PsiElement key = kv.getKey();
        if (PsiTreeUtil.isAncestor(key, element, false)) {
          consumer.consume(kv);
        }
      }
    }
  }
}

