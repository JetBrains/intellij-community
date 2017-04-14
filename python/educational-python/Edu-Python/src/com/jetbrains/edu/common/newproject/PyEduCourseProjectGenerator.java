/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.edu.common.newproject;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.util.BooleanFunction;
import com.jetbrains.edu.learning.newproject.EduCourseProjectGenerator;
import com.jetbrains.edu.learning.PyStudyDirectoryProjectGenerator;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PyEduCourseProjectGenerator implements EduCourseProjectGenerator {
  private final PyStudyDirectoryProjectGenerator myGenerator;

  public PyEduCourseProjectGenerator() {
    this.myGenerator = new PyStudyDirectoryProjectGenerator();
  }

  @NotNull
  @Override
  public DirectoryProjectGenerator getDirectoryProjectGenerator() {
    return myGenerator;
  }

  @Nullable
  @Override
  public Object getProjectSettings() {
    return myGenerator.getProjectSettings();
  }

  @Override
  public void setCourse(@NotNull Course course) {
    myGenerator.setSelectedCourse(course);
  }

  @Override
  public ValidationResult validate(@NotNull String path) {
    myGenerator.setValidationResult(ValidationResult.OK);
    return myGenerator.validate(path);
  }

  @Override
  public boolean beforeProjectGenerated() {
    BooleanFunction<PythonProjectGenerator> function =
      myGenerator.beforeProjectGenerated(null);
    return function != null && function.fun(myGenerator);
  }

  @Override
  public void afterProjectGenerated(@NotNull Project project) {
    PyNewProjectSettings settings = (PyNewProjectSettings)myGenerator.getProjectSettings();
    Sdk sdk = settings.getSdk();

    if (sdk == null) {
      myGenerator.createAndAddVirtualEnv(project, settings);
      sdk = settings.getSdk();
    }

    SdkConfigurationUtil.setDirectoryProjectSdk(project, sdk);
    final List<Sdk> sdks = PythonSdkType.getAllSdks();
    for (Sdk s : sdks) {
      final SdkAdditionalData additionalData = s.getSdkAdditionalData();
      if (additionalData instanceof PythonSdkAdditionalData) {
        ((PythonSdkAdditionalData)additionalData).reassociateWithCreatedProject(project);
      }
    }
  }
}
