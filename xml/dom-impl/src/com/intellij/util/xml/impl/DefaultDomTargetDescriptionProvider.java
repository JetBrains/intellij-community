// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomDescriptionProvider;
import com.intellij.pom.PomTarget;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.usageView.UsageViewLongNameLocation;
import com.intellij.usageView.UsageViewNodeTextLocation;
import com.intellij.usageView.UsageViewTypeLocation;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.ElementPresentation;
import com.intellij.util.xml.ElementPresentationTemplate;
import org.jetbrains.annotations.NotNull;

final class DefaultDomTargetDescriptionProvider extends PomDescriptionProvider {
  @Override
  public String getElementDescription(@NotNull PomTarget element, @NotNull ElementDescriptionLocation location) {
    if (!(element instanceof DomTarget target)) return null;

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
