/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class MavenIcons {
  public static final Icon MAVEN_ICON = IconLoader.getIcon("/images/mavenLogo.png");
  public static final Icon MAVEN_PROJECT_ICON = IconLoader.getIcon("/images/mavenProject.png");
  public static final Icon OPEN_PROFILES_ICON = IconLoader.getIcon("/images/profilesOpen.png");
  public static final Icon CLOSED_PROFILES_ICON = IconLoader.getIcon("/images/profilesClosed.png");
  public static final Icon OPEN_PHASES_ICON = IconLoader.getIcon("/images/phasesOpen.png");
  public static final Icon CLOSED_PHASES_ICON = IconLoader.getIcon("/images/phasesClosed.png");
  public static final Icon PHASE_ICON = IconLoader.getIcon("/images/phase.png");
  public static final Icon OPEN_PLUGINS_ICON = IconLoader.getIcon("/images/phasesOpen.png");
  public static final Icon CLOSED_PLUGINS_ICON = IconLoader.getIcon("/images/phasesClosed.png");
  public static final Icon PLUGIN_ICON = IconLoader.getIcon("/images/mavenPlugin.png");
  public static final Icon REPOSITORY_ICON = IconLoader.getIcon("/images/mavenPlugin.png");
  public static final Icon PLUGIN_GOAL_ICON = IconLoader.getIcon("/images/pluginGoal.png");
  public static final Icon OPEN_DEPENDENCIES_ICON = IconLoader.getIcon("/nodes/ppLibOpen.png");
  public static final Icon CLOSED_DEPENDENCIES_ICON = IconLoader.getIcon("/nodes/ppLibClosed.png");
  public static final Icon DEPENDENCY_ICON = IconLoader.getIcon("/nodes/ppLib.png");
  public static final Icon OPEN_MODULES_ICON = IconLoader.getIcon("/images/modulesOpen.png");
  public static final Icon CLOSED_MODULES_ICON = IconLoader.getIcon("/images/modulesClosed.png");
  
  public static final Icon OVERRIDING_DEPENDENCY = IconLoader.getIcon("/images/overridingDependency.png");
  public static final Icon OVERRIDEN_DEPENDENCY = IconLoader.getIcon("/images/overridenDependency.png");
}
