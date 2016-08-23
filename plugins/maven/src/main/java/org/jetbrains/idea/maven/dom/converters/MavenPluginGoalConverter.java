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
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    return getVariants(context).contains(s) ? s : null;
  }

  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
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
  public PsiElement resolve(String text, ConvertContext context) {
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
