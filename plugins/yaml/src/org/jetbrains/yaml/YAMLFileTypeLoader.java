// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 * @deprecated to be removed in 2020.1
 */
@ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
@Deprecated
public class YAMLFileTypeLoader extends FileTypeFactory {
  @Override
  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
  }
}