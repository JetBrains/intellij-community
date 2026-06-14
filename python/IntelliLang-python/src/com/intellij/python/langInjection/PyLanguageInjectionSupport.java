// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.langInjection;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.Consumer;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyElement;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Internal
public final class PyLanguageInjectionSupport extends AbstractLanguageInjectionSupport {
  private static final @NonNls String SUPPORT_ID = "python";

  @Override
  public @NotNull String getId() {
    return SUPPORT_ID;
  }

  @Override
  public Class @NotNull [] getPatternClasses() {
    return new Class[] { PythonPatterns.class };
  }

  @Override
  public boolean isApplicableTo(PsiLanguageInjectionHost host) {
    return host instanceof PyElement;
  }

  @Override
  public AnAction @NotNull [] createAddActions(final Project project, final Consumer<? super BaseInjection> consumer) {
    // The default action resolves its icon via the file type registered for the support id ("python"),
    // but the Python file type is registered under the "py" extension, so the lookup fails. Set it explicitly.
    AnAction defaultAddAction = createDefaultAddAction(project, consumer, this);
    defaultAddAction.getTemplatePresentation().setIcon(PythonFileType.INSTANCE.getIcon());
    return new AnAction[]{ defaultAddAction };
  }

  @Override
  public @Nullable String getHelpId() {
    return "reference.settings.language.injection.generic.python";
  }
}
