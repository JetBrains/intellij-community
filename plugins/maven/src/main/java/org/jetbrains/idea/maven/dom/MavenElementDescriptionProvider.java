/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.usageView.UsageViewTypeLocation;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenElementDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(@NotNull PsiElement element, @NotNull ElementDescriptionLocation location) {
    if (!MavenDomUtil.isMavenFile(element)) return null;

    boolean property = MavenDomUtil.isMavenProperty(element);

    if (location instanceof UsageViewTypeLocation) {
      return property ? "Property" : "Model Property";
    }

    if (property) return UsageViewUtil.getDescriptiveName(element);

    List<String> path = new ArrayList<String>();
    do {
      path.add(UsageViewUtil.getDescriptiveName(element));
    }
    while ((element = PsiTreeUtil.getParentOfType(element, XmlTag.class)) != null);
    Collections.reverse(path);
    return StringUtil.join(path, ".");
  }
}
