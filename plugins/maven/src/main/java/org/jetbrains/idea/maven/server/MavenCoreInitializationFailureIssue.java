// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.build.FileNavigatable;
import com.intellij.build.FilePosition;
import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.Navigatable;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenSettingsQuickFix;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class MavenCoreInitializationFailureIssue implements BuildIssue {
  private @NlsSafe final String myMessage;
  private final Set<String> myMultimoduleDirectories;
  private final String myMavenVersion;
  private final MavenId myUnresolvedExtensionId;

  public MavenCoreInitializationFailureIssue(@NlsSafe String message,
                                             @NotNull Set<String> multimoduleDirectories,
                                             @Nullable String mavenVersion,
                                             @Nullable MavenId unresolvedExtensionId) {
    myMessage = message;
    myMultimoduleDirectories = multimoduleDirectories;
    myMavenVersion = mavenVersion;
    myUnresolvedExtensionId = unresolvedExtensionId;
  }

  @NotNull
  @Override
  public String getTitle() {
    return SyncBundle.message("maven.core.plexus.init.issue.title");
  }

  @NotNull
  @Override
  public String getDescription() {
    StringBuilder desc = new StringBuilder(SyncBundle.message("maven.core.plexus.init.issue.description"));
    if (myMavenVersion == null || VersionComparatorUtil.compare("3.8.5", myMavenVersion) <= 0) {
      desc.append("\n").append(SyncBundle.message("maven.core.plexus.init.issue.fix.downgrade", OpenMavenSettingsQuickFix.ID));
    }
    desc.append("\n").append(SyncBundle.message("maven.core.plexus.init.issue.fix.remove", RestartMavenEmbeddersQuickFix.ID));

    desc.append("\n\n\n").append(SyncBundle.message("maven.core.plexus.init.issue.description.exception"));

    desc.append("\n").append(myMessage);
    return desc.toString(); //NON-NLS
  }

  @NotNull
  @Override
  public List<BuildIssueQuickFix> getQuickFixes() {
    return List.of(
      new RestartMavenEmbeddersQuickFix(),
      new OpenMavenSettingsQuickFix()
    );
  }

  @Nullable
  @Override
  public Navigatable getNavigatable(@NotNull Project project) {
    for (String directory : myMultimoduleDirectories) {
      Path extensions = Path.of(directory).resolve(".mvn").resolve("extensions.xml");
      if (!extensions.toFile().isFile()) continue;
      if (MavenUtil.containsDeclaredExtension(extensions, myUnresolvedExtensionId)) {
        return new FileNavigatable(project, new FilePosition(extensions.toFile(), 0, 0));
      }
    }
    return null;
  }
}
