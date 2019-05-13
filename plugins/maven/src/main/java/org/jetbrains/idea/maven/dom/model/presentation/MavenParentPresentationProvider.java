/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.model.presentation;

import com.intellij.ide.presentation.PresentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;

/**
 *
 */
public class MavenParentPresentationProvider extends PresentationProvider<MavenDomParent> {
  @Nullable
  @Override
  public String getName(MavenDomParent mavenDomParent) {
    String groupId = mavenDomParent.getGroupId().getStringValue();
    String artifactId = mavenDomParent.getGroupId().getStringValue();
    String version = mavenDomParent.getVersion().getStringValue();

    String relativePath = mavenDomParent.getRelativePath().getStringValue();

    return "Parent (" + groupId + ':' + artifactId + ':' + version + (StringUtil.isEmpty(relativePath) ? "" : ", relativePath=\"" + relativePath) +"\")";
  }
}
