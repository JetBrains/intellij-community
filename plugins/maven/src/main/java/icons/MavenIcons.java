// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.ui.IconManager;

import javax.swing.*;

import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class MavenIcons {
  private static Icon load(String path) {
    return IconManager.getInstance().getIcon(path, MavenIcons.class);
  }

  private static Icon load(String path, Class<?> clazz) {
    return IconManager.getInstance().getIcon(path, clazz);
  }

  /** 12x12 */ public static final Icon ChildrenProjects = load("/images/childrenProjects.svg");
  /** 16x16 */ public static final Icon ExecuteMavenGoal = load("/images/executeMavenGoal.svg");
  /** 16x16 */ public static final Icon MavenPlugin = load("/images/mavenPlugin.svg");
  /** 16x16 */ public static final Icon MavenProject = load("/images/mavenProject.svg");
  /** 16x16 */ public static final Icon ModulesClosed = load("/images/modulesClosed.svg");
  /** 12x12 */ public static final Icon ParentProject = load("/images/parentProject.svg");
  /** 16x16 */ public static final Icon PluginGoal = load("/images/pluginGoal.svg");
  /** 16x16 */ public static final Icon ProfilesClosed = load("/images/profilesClosed.svg");
  /** 13x13 */ public static final Icon ToolWindowMaven = load("/images/toolWindowMaven.svg");
  /** 16x16 */ public static final Icon UpdateFolders = load("/images/updateFolders.svg");

  /** @deprecated to be removed in IDEA 2020 - use OpenapiIcons.RepositoryLibraryLogo */
  @SuppressWarnings("unused")
  @Deprecated
  @ScheduledForRemoval(inVersion = "2020.1")
  public static final Icon MavenLogo = load("/icons/repositoryLibraryLogo.svg", icons.OpenapiIcons.class);

  /** @deprecated to be removed in IDEA 2020 - use AllIcons.Actions.OfflineMode */
  @SuppressWarnings("unused")
  @Deprecated
  @ScheduledForRemoval(inVersion = "2020.1")
  public static final Icon OfflineMode = load("/actions/offlineMode.svg", com.intellij.icons.AllIcons.class);

  /** @deprecated to be removed in IDEA 2020 - use ExternalSystemIcons.Task */
  @SuppressWarnings("unused")
  @Deprecated
  @ScheduledForRemoval(inVersion = "2020.1")
  public static final Icon Phase = load("/icons/task.svg", icons.ExternalSystemIcons.class);

  /** @deprecated to be removed in IDEA 2020 - use ExternalSystemIcons.TaskGroup */
  @SuppressWarnings("unused")
  @Deprecated
  @ScheduledForRemoval(inVersion = "2020.1")
  public static final Icon PhasesClosed = load("/icons/taskGroup.svg", icons.ExternalSystemIcons.class);
}
