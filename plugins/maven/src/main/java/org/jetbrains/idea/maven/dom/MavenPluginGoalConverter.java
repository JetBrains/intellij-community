package org.jetbrains.idea.maven.dom;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.plugin.MavenPluginModel;
import org.jetbrains.idea.maven.dom.plugin.Mojo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MavenPluginGoalConverter extends ResolvingConverter<String> {
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    return getVariants(context).contains(s) ? s : null;
  }

  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    List<String> result = new ArrayList<String>();
    MavenPluginModel model = MavenPluginDomUtil.getMavenPlugin(context.getInvocationElement());
    for (Mojo each : model.getMojos().getMojos()) {
      String goal = each.getGoal().getStringValue();
      if (goal != null) result.add(goal);
    }
    return result;
  }

  @Override
  public PsiElement resolve(String text, ConvertContext context) {
    MavenPluginModel model = MavenPluginDomUtil.getMavenPlugin(context.getInvocationElement());
    for (Mojo each : model.getMojos().getMojos()) {
      String goal = each.getGoal().getStringValue();
      if (text.equals(goal)) return each.getXmlElement();
    }
    return super.resolve(text, context);
  }
}
