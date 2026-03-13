// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.groovy;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionWeigher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.util.dynamicMembers.DynamicMemberUtils;

/**
 * @author Vladislav.Soroka
 */
public final class MavenGroovyPomCompletionWeigher extends CompletionWeigher {

  @Override
  public Comparable weigh(@NotNull LookupElement element, @NotNull CompletionLocation location) {
    PsiFile containingFile = location.getBaseCompletionParameters().getPosition().getContainingFile();
    if (!(containingFile instanceof GroovyFileBase)) {
      return null;
    }

    if (!"pom.groovy".equals(containingFile.getName())) return null;

    Object o = element.getObject();
    if (o instanceof ResolveResult) {
      o = ((ResolveResult)o).getElement();
    }

    if (o instanceof DynamicMemberUtils.DynamicElement) {
      return 1;
    }
    else {
      return -1;
    }
  }
}
