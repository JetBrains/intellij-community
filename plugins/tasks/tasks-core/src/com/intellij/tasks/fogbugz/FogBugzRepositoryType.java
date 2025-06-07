// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.fogbugz;

import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.BaseRepositoryType;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author mkennedy
 */
public class FogBugzRepositoryType extends BaseRepositoryType<FogBugzRepository> {

  @Override
  public @NotNull String getName() {
    return "FogBugz";
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Fogbugz;
  }

  @Override
  public @NotNull TaskRepository createRepository() {
    return new FogBugzRepository(this);
  }

  @Override
  public Class<FogBugzRepository> getRepositoryClass() {
    return FogBugzRepository.class;
  }
}
