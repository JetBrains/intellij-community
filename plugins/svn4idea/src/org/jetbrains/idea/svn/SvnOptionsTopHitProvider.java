/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.PublicMethodBasedOptionDescription;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey.Malenkov
 */
public final class SvnOptionsTopHitProvider extends OptionsTopHitProvider {
  @Override
  public String getId() {
    return "vcs";
  }

  @NotNull
  @Override
  public Collection<BooleanOptionDescription> getOptions(@Nullable Project project) {
    if (project != null) {
      for (VcsDescriptor descriptor : ProjectLevelVcsManager.getInstance(project).getAllVcss()) {
        if ("Subversion".equals(descriptor.getDisplayName())) {
          final SvnConfiguration config = SvnConfiguration.getInstance(project);
          return Collections.unmodifiableCollection(Arrays.asList(
            option(config, "Subversion: Update administrative information only in changed subtrees", "isUpdateLockOnDemand", "setUpdateLockOnDemand"),
            option(config, "Subversion: Check svn:mergeinfo in target subtree when preparing for merge", "isCheckNestedForQuickMerge", "setCheckNestedForQuickMerge"),
            option(config, "Subversion: Show merge source in history and annotations", "isShowMergeSourcesInAnnotate", "setShowMergeSourcesInAnnotate"),
            option(config, "Subversion: Ignore whitespace differences in annotations", "isIgnoreSpacesInAnnotate", "setIgnoreSpacesInAnnotate"),
            option(config, "Subversion: Use IDEA general proxy settings as default for Subversion", "isIsUseDefaultProxy", "setIsUseDefaultProxy")));
        }
      }
    }
    return Collections.emptyList();
  }

  private static BooleanOptionDescription option(final Object instance, String option, String getter, String setter) {
    return new PublicMethodBasedOptionDescription(option, "vcs.Subversion", getter, setter) {
      @Override
      public Object getInstance() {
        return instance;
      }
    };
  }
}
