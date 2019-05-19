/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageSchemaDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenConstants;

/**
 * @author yole
 */
public class MavenFileTypeFactory extends FileTypeFactory implements FileTypeUsageSchemaDescriptor {
  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    consumer.consume(XmlFileType.INSTANCE, MavenConstants.POM_EXTENSION);
  }

  @Override
  public boolean describes(@NotNull VirtualFile file) {
    return file.getFileType() == XmlFileType.INSTANCE && FileUtil.namesEqual(file.getName(), MavenConstants.POM_XML);
  }
}
