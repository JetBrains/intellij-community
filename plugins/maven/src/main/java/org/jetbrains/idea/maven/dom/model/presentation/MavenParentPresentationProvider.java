// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.model.presentation;

import com.intellij.ide.presentation.PresentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;

/**
 *
 */
public class MavenParentPresentationProvider extends PresentationProvider<MavenDomParent> {
  @Override
  public @Nullable String getName(MavenDomParent mavenDomParent) {
    String groupId = mavenDomParent.getGroupId().getStringValue();
    String artifactId = mavenDomParent.getGroupId().getStringValue();
    String version = mavenDomParent.getVersion().getStringValue();

    String relativePath = mavenDomParent.getRelativePath().getStringValue();

    return "Parent (" + groupId + ':' + artifactId + ':' + version + (StringUtil.isEmpty(relativePath) ? "" : ", relativePath=\"" + relativePath) +"\")";
  }
}
