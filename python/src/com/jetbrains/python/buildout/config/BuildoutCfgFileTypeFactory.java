// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config;

import com.intellij.openapi.fileTypes.ExactFileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.jetbrains.python.buildout.BuildoutFacet;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class BuildoutCfgFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
    consumer.consume(BuildoutCfgFileType.INSTANCE, new ExactFileNameMatcher(BuildoutFacet.BUILDOUT_CFG, true));
  }
}