// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.psi.PsiElement;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenPluginDomUtil;
import org.jetbrains.idea.maven.dom.plugin.MavenDomMojo;
import org.jetbrains.idea.maven.dom.plugin.MavenDomPluginModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MavenPluginGoalConverter extends ResolvingConverter<String> implements MavenDomSoftAwareConverter {
  @Override
  public String fromString(@Nullable @NonNls String s, @NotNull ConvertContext context) {
    return getVariants(context).contains(s) ? s : null;
  }

  @Override
  public String toString(@Nullable String s, @NotNull ConvertContext context) {
    return s;
  }

  @Override
  public @NotNull Collection<String> getVariants(@NotNull ConvertContext context) {
    MavenDomPluginModel model = MavenPluginDomUtil.getMavenPluginModel(context.getInvocationElement());
    if (model == null) return Collections.emptyList();

    List<String> result = new ArrayList<>();
    for (MavenDomMojo each : model.getMojos().getMojos()) {
      String goal = each.getGoal().getStringValue();
      if (goal != null) result.add(goal);
    }
    return result;
  }

  @Override
  public PsiElement resolve(String text, @NotNull ConvertContext context) {
    MavenDomPluginModel model = MavenPluginDomUtil.getMavenPluginModel(context.getInvocationElement());
    if (model == null) return null;

    for (MavenDomMojo each : model.getMojos().getMojos()) {
      String goal = each.getGoal().getStringValue();
      if (text.equals(goal)) return each.getXmlElement();
    }
    return super.resolve(text, context);
  }

  @Override
  public boolean isSoft(@NotNull DomElement element) {
    return MavenPluginDomUtil.getMavenPluginModel(element) == null;
  }
}
