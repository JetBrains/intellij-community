/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomDescriptionProvider;
import com.intellij.pom.PomTarget;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.usageView.UsageViewNodeTextLocation;
import com.intellij.usageView.UsageViewTypeLocation;
import com.intellij.usageView.UsageViewLongNameLocation;
import com.intellij.util.xml.*;
import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DefaultDomTargetDescriptionProvider extends PomDescriptionProvider {
  @Override
  public String getElementDescription(@NotNull PomTarget element, @NotNull ElementDescriptionLocation location) {
    if (!(element instanceof DomTarget)) return null;

    final DomTarget target = (DomTarget)element;

    DomElement domElement = target.getDomElement();
    final ElementPresentationTemplate template = domElement.getChildDescription().getPresentationTemplate();
    final ElementPresentation presentation = template != null ? template.createPresentation(domElement) : domElement.getPresentation();

    if (location == UsageViewTypeLocation.INSTANCE) {
      return presentation.getTypeName();
    }
    if (location == UsageViewNodeTextLocation.INSTANCE || location == UsageViewLongNameLocation.INSTANCE) {
      return presentation.getTypeName() + " " + StringUtil.notNullize(presentation.getElementName(), "''");
    }
    if (location instanceof HighlightUsagesDescriptionLocation) {
      return presentation.getTypeName();
    }
    return null;
  }

}
