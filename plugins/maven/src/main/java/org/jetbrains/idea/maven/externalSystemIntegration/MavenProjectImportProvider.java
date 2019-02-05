// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration;

import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenUtil;

public class MavenProjectImportProvider extends AbstractExternalProjectImportProvider {

  public MavenProjectImportProvider(MavenProjectImportBuilder builder) {
    super(builder, MavenConstants.SYSTEM_ID);
  }

  @Override
  protected boolean canImportFromFile(VirtualFile file) {
    return MavenUtil.isPomFile(file);
  }

  @Nullable
  @Override
  public String getFileSample() {
    return "<b>Maven</b> build file (pom.xml)";
  }


}
