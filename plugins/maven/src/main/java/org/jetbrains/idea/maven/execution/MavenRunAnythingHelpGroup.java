// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider;
import com.intellij.ide.actions.runAnything.groups.RunAnythingHelpGroup;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author ibessonov
 */
public class MavenRunAnythingHelpGroup extends RunAnythingHelpGroup {

  @NotNull
  @Override
  public String getTitle() {
    return "Maven";
  }

  @NotNull
  @Override
  public Collection<RunAnythingProvider> getProviders() {
    return ContainerUtil.immutableSingletonList(RunAnythingProvider.EP_NAME.findExtension(MavenRunAnythingProvider.class));
  }
}
