package org.jetbrains.idea.maven.dom.converters;

import com.intellij.psi.PsiElement;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.plugin.MavenDomPluginModel;
import org.jetbrains.idea.maven.dom.plugin.MavenDomMojo;
import org.jetbrains.idea.maven.dom.MavenPluginDomUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MavenPluginGoalConverter extends MavenPropertyResolvingConverter<String> {
  @Override
  public String fromResolvedString(@Nullable @NonNls String s, ConvertContext context) {
    return getVariants(context).contains(s) ? s : null;
  }

  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    MavenDomPluginModel model = MavenPluginDomUtil.getMavenPlugin(context.getInvocationElement());
    if (model == null) return Collections.emptyList();

    List<String> result = new ArrayList<String>();
    for (MavenDomMojo each : model.getMojos().getMojos()) {
      String goal = each.getGoal().getStringValue();
      if (goal != null) result.add(goal);
    }
    return result;
  }

  @Override
  public PsiElement resolve(String text, ConvertContext context) {
    MavenDomPluginModel model = MavenPluginDomUtil.getMavenPlugin(context.getInvocationElement());
    if (model == null) return null;
    
    for (MavenDomMojo each : model.getMojos().getMojos()) {
      String goal = each.getGoal().getStringValue();
      if (text.equals(goal)) return each.getXmlElement();
    }
    return super.resolve(text, context);
  }
}
