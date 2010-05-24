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

import com.intellij.openapi.actionSystem.DataKey;
import org.jetbrains.idea.maven.project.MavenArtifact;

import javax.swing.*;
import java.util.Collection;
import java.util.List;


public class MavenDataKeys {
  public static final DataKey<List<String>> MAVEN_GOALS = DataKey.create("MAVEN_GOALS");
  public static final DataKey<List<String>> MAVEN_PROFILES = DataKey.create("MAVEN_PROFILES");
  public static final DataKey<Collection<MavenArtifact>> MAVEN_DEPENDENCIES = DataKey.create("MAVEN_DEPENDENCIES");
  public static final DataKey<JTree> MAVEN_PROJECTS_TREE = DataKey.create("MAVEN_PROJECTS_TREE");

  private MavenDataKeys() {
  }
}
