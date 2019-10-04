/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.plugins.groovy;

import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageSchemaDescriptor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author Vladislav.Soroka
 */
public class MavenGroovyScriptTypeDetector extends GroovyScriptTypeDetector implements FileTypeUsageSchemaDescriptor {
  public MavenGroovyScriptTypeDetector() {
    super(MavenGroovyPomScriptType.INSTANCE);
  }

  @Override
  public boolean isSpecificScriptFile(@NotNull GroovyFile script) {
    return isMavenGroovyScript(script.getViewProvider().getVirtualFile());
  }

  @Override
  public boolean describes(@NotNull VirtualFile file) {
    return isMavenGroovyScript(file);
  }

  private static boolean isMavenGroovyScript(@NotNull VirtualFile file) {
    return FileTypeRegistry.getInstance().isFileOfType(file, GroovyFileType.GROOVY_FILE_TYPE) && "pom".equals(file.getNameWithoutExtension());
  }
}
