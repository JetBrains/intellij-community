// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.pom.java.AcceptedLanguageLevelsSettings;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.SyncBundle;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class NonAcceptedJavaLevelIssue implements BuildIssue {
  private final LanguageLevel myLevel;

  public NonAcceptedJavaLevelIssue(LanguageLevel level) { myLevel = level; }

  @NotNull
  @Override
  public String getTitle() {
    return SyncBundle.message("maven.language.level.unaccepted.title", myLevel.getPresentableText());
  }

  @NotNull
  @Override
  public String getDescription() {
    return SyncBundle.message("maven.language.level.unaccepted.description", myLevel.getPresentableText(), MyQuickFix.ID);
  }

  @NotNull
  @Override
  public List<BuildIssueQuickFix> getQuickFixes() {
    return Collections.singletonList(new MyQuickFix(myLevel));
  }

  @Nullable
  @Override
  public Navigatable getNavigatable(@NotNull Project project) {
    return null;
  }

  private static class MyQuickFix implements BuildIssueQuickFix {
    static String ID = "maven_accept_language_level_issue_quick_fix";
    private final LanguageLevel myLevel;

    MyQuickFix(LanguageLevel level) {

      myLevel = level;
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    @NotNull
    @Override
    public CompletableFuture<?> runQuickFix(@NotNull Project project, @NotNull DataContext dataContext) {
      AcceptedLanguageLevelsSettings.showNotificationToAccept(project, myLevel);
      return CompletableFuture.completedFuture(null);
    }
  }
}
