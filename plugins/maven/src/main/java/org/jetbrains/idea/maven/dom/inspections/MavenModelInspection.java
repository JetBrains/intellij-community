// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.inspections;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.converters.MavenDomSoftAwareConverter;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public final class MavenModelInspection extends BasicDomElementsInspection<MavenDomProjectModel> {
  public MavenModelInspection() {
    super(MavenDomProjectModel.class);
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return MavenDomBundle.message("inspection.group");
  }

  @Override
  public @NotNull String getShortName() {
    return "MavenModelInspection";
  }

  private static boolean isElementInsideManagedFile(GenericDomValue value) {
    VirtualFile virtualFile = DomUtil.getFile(value).getVirtualFile();
    if (virtualFile == null) {
      return false;
    }

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(value.getManager().getProject());

    return projectsManager.findProject(virtualFile) != null;
  }

  @Override
  protected boolean shouldCheckResolveProblems(GenericDomValue value) {
    if (!isElementInsideManagedFile(value)) {
      return false;
    }

    Converter converter = value.getConverter();
    if (converter instanceof MavenDomSoftAwareConverter) {
      return !((MavenDomSoftAwareConverter)converter).isSoft(value);
    }

    return true;
  }
}