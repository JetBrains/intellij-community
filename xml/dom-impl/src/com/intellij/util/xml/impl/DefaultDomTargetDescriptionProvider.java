/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomDescriptionProvider;
import com.intellij.pom.PomTarget;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.usageView.UsageViewNodeTextLocation;
import com.intellij.usageView.UsageViewTypeLocation;
import com.intellij.usageView.UsageViewLongNameLocation;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.TypeNameManager;
import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DefaultDomTargetDescriptionProvider extends PomDescriptionProvider {
  public String getElementDescription(@NotNull PomTarget element, @NotNull ElementDescriptionLocation location) {
    if (!(element instanceof DomTarget)) return null;

    final DomTarget target = (DomTarget)element;

    if (location == UsageViewTypeLocation.INSTANCE) {
      return TypeNameManager.getTypeName(target.getDomElement().getClass());
    }
    if (location == UsageViewNodeTextLocation.INSTANCE || location == UsageViewLongNameLocation.INSTANCE) {
      return TypeNameManager.getTypeName(target.getDomElement().getClass()) + " " + StringUtil.notNullize(target.getName(), "''");
    }
    if (location instanceof HighlightUsagesDescriptionLocation) {
      return TypeNameManager.getTypeName(target.getDomElement().getClass());
    }

    return null;
  }

}
