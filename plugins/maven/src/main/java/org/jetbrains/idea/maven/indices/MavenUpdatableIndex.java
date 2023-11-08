// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

public interface MavenUpdatableIndex {
  void updateOrRepair(boolean fullUpdate, MavenProgressIndicator progress, boolean multithreaded)
    throws MavenProcessCanceledException;
}
