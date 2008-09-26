/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author max
 */
public abstract class LocalChangeList implements Cloneable, ChangeList {
  public static LocalChangeList createEmptyChangeList(Project project, @NotNull String name) {
    return VcsContextFactory.SERVICE.getInstance().createLocalChangeList(project, name);
  }

  public abstract Collection<Change> getChanges();

  @NotNull
  public abstract String getName();

  public abstract void setName(@NotNull String name);

  public abstract String getComment();

  public abstract void setComment(String comment);

  public abstract boolean isDefault();

  public abstract boolean isInUpdate();

  public abstract boolean isReadOnly();

  public abstract void setReadOnly(boolean isReadOnly);

  public abstract LocalChangeList copy();
}
