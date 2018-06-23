// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public class MavenIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, MavenIcons.class);
  }

  private static Icon load(String path, Class<?> clazz) {
    return IconLoader.getIcon(path, clazz);
  }

  public static final Icon ChildrenProjects = load("/images/childrenProjects.png"); // 12x12
  public static final Icon ExecuteMavenGoal = load("/images/executeMavenGoal.png"); // 16x16
  public static final Icon MavenLogo = load("/images/mavenLogo.png"); // 16x16
  public static final Icon MavenPlugin = load("/images/mavenPlugin.png"); // 16x16
  public static final Icon MavenProject = load("/images/mavenProject.png"); // 16x16
  public static final Icon ModulesClosed = load("/images/modulesClosed.png"); // 16x16

  /** @deprecated to be removed in IDEA 2020 - use AllIcons.Actions.OfflineMode */
  @SuppressWarnings("unused")
  @Deprecated
  public static final Icon OfflineMode = load("/actions/offlineMode.svg", com.intellij.icons.AllIcons.class);
  public static final Icon ParentProject = load("/images/parentProject.png"); // 12x12
  public static final Icon Phase = load("/images/phase.png"); // 16x16
  public static final Icon PhasesClosed = load("/images/phasesClosed.png"); // 16x16
  public static final Icon PluginGoal = load("/images/pluginGoal.png"); // 16x16
  public static final Icon ProfilesClosed = load("/images/profilesClosed.png"); // 16x16
  public static final Icon ToolWindowMaven = load("/images/toolWindowMaven.svg"); // 13x13
  public static final Icon UpdateFolders = load("/images/updateFolders.png"); // 16x16
}
