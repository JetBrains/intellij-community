// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.javadoc;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.javadoc.CustomJavadocTagProvider;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MojoClassAnnotationTagProvider implements CustomJavadocTagProvider {
  private static final String[] ANNOTATION_NAMES = {
      "goal",
      "requiresDependencyResolution",
      "requiresProject",
      "requiresReports",
      "aggregator",
      "requiresOnline",
      "requiresDirectInvocation",
      "phase",
      "execute"
  };

  @Override
  public List<JavadocTagInfo> getSupportedTags() {
    return ContainerUtil.map(ANNOTATION_NAMES, name -> new MojoAnnotationInfo(name));
  }
}

class MojoAnnotationInfo implements JavadocTagInfo {
  private static final String BASE_CLASS = "org.apache.maven.plugin.Mojo";

  private final String myName;

  MojoAnnotationInfo(@NotNull String name) {
    myName = name;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isInline() {
    return false;
  }

  @Override
  public boolean isValidInContext(PsiElement element) {
    if (element instanceof PsiClass psiClass) {
      return InheritanceUtil.isInheritor(psiClass, BASE_CLASS);
    }
    return false;
  }

  @Override
  public @Nullable String checkTagValue(PsiDocTagValue value) {
    return null;
  }

  @Override
  public @Nullable PsiReference getReference(PsiDocTagValue value) {
    return null;
  }
}