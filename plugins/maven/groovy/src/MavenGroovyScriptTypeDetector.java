// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.groovy;

import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageSchemaDescriptor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author Vladislav.Soroka
 */
public final class MavenGroovyScriptTypeDetector extends GroovyScriptTypeDetector implements FileTypeUsageSchemaDescriptor {
  public MavenGroovyScriptTypeDetector() {
    super(MavenGroovyPomScriptType.INSTANCE);
  }

  @Override
  public boolean isSpecificScriptFile(@NotNull GroovyFile script) {
    return isMavenGroovyScript(script.getViewProvider().getVirtualFile());
  }

  @Override
  public boolean describes(@NotNull Project project, @NotNull VirtualFile file) {
    return isMavenGroovyScript(file);
  }

  private static boolean isMavenGroovyScript(@NotNull VirtualFile file) {
    return FileTypeRegistry.getInstance().isFileOfType(file, GroovyFileType.GROOVY_FILE_TYPE) && "pom".equals(file.getNameWithoutExtension());
  }
}
