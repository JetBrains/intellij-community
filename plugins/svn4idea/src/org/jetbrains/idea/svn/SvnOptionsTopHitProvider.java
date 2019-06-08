// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.ide.ui.OptionsSearchTopHitProvider;
import com.intellij.ide.ui.PublicMethodBasedOptionDescription;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey.Malenkov
 */
final class SvnOptionsTopHitProvider implements OptionsSearchTopHitProvider.ProjectLevelProvider {
  @NotNull
  @Override
  public String getId() {
    return "vcs";
  }

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions(@NotNull Project project) {
    for (VcsDescriptor descriptor : ProjectLevelVcsManager.getInstance(project).getAllVcss()) {
      if ("Subversion".equals(descriptor.getDisplayName())) {
        final SvnConfiguration config = SvnConfiguration.getInstance(project);
        return Collections.unmodifiableCollection(Arrays.asList(
          option(config, "Subversion: Check svn:mergeinfo in target subtree when preparing for merge", "isCheckNestedForQuickMerge", "setCheckNestedForQuickMerge"),
          option(config, "Subversion: Show merge source in history and annotations", "isShowMergeSourcesInAnnotate", "setShowMergeSourcesInAnnotate"),
          option(config, "Subversion: Ignore whitespace differences in annotations", "isIgnoreSpacesInAnnotate", "setIgnoreSpacesInAnnotate"),
          option(config, "Subversion: Use IDEA general proxy settings as default for Subversion", "isUseDefaultProxy", "setUseDefaultProxy")));
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
