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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;

public abstract class MavenSimpleProjectComponent extends AbstractProjectComponent {
  protected MavenSimpleProjectComponent(Project project) {
    super(project);
  }

  protected boolean isNormalProject() {
    return !isUnitTestMode() && !isHeadless() && !isDefault();
  }

  protected boolean isNoBackgroundMode() {
    return MavenUtil.isNoBackgroundMode();
  }

  protected boolean isUnitTestMode() {
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  protected boolean isHeadless() {
    return ApplicationManager.getApplication().isHeadlessEnvironment();
  }

  protected boolean isDefault() {
    return myProject.isDefault();
  }
}
