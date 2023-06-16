// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Old internal Maven statistics collector no more needed
 */
@Deprecated
@ApiStatus.Internal
public interface MavenSettingsObservable {
  void registerSettingsWatcher(@NotNull MavenRCSettingsWatcher watcher);
}
