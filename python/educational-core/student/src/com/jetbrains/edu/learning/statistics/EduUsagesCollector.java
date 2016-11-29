/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.edu.learning.statistics;

import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class EduUsagesCollector extends UsagesCollector {
  private static final String GROUP_ID = "educational";

  private final FactoryMap<String, UsageDescriptor> myUsageDescriptors = new FactoryMap<String, UsageDescriptor>() {
    @Nullable
    @Override
    protected UsageDescriptor create(String key) {
      return new UsageDescriptor(key, 0);
    }
  };

  public static void projectTypeCreated(@NotNull String projectTypeId) {
    ServiceManager.getService(EduUsagesCollector.class).myUsageDescriptors.get("project.created." + projectTypeId).advance();
  }

  public static void projectTypeOpened(@NotNull String projectTypeId) {
    ServiceManager.getService(EduUsagesCollector.class).myUsageDescriptors.get("project.opened." + projectTypeId).advance();
  }

  public static void taskChecked() {
    ServiceManager.getService(EduUsagesCollector.class).myUsageDescriptors.get("checkTask.").advance();
  }

  public static void hintShown() {
    ServiceManager.getService(EduUsagesCollector.class).myUsageDescriptors.get("showHint.").advance();
  }

  public static void taskNavigation() {
    ServiceManager.getService(EduUsagesCollector.class).myUsageDescriptors.get("navigateToTask.").advance();
  }

  public static void courseUploaded() {
    ServiceManager.getService(EduUsagesCollector.class).myUsageDescriptors.get("uploadCourse.").advance();
  }

  public static void createdCourseArchive() {
    ServiceManager.getService(EduUsagesCollector.class).myUsageDescriptors.get("courseArchive.").advance();
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() throws CollectUsagesException {
    HashSet<UsageDescriptor> descriptors = new HashSet<>();
    descriptors.addAll(myUsageDescriptors.values());
    myUsageDescriptors.clear();
    return descriptors;
  }


  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID);
  }
}
