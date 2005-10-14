package com.intellij.refactoring.util.classMembers;

import com.intellij.util.containers.HashMap;

/**
 * @author dsl
 */
public class MemberInfoTooltipManager {
  private final HashMap myTooltips;
  private final TooltipProvider myProvider;

  public interface TooltipProvider {
    String getTooltip(MemberInfo memberInfo);
  }

  public static class DefaultTooltipProvider implements TooltipProvider {
    public static final DefaultTooltipProvider INSTANCE = new DefaultTooltipProvider();
    private DefaultTooltipProvider() {}
    public String getTooltip(MemberInfo memberInfo) {
      return null;
    }
  }

  public MemberInfoTooltipManager(TooltipProvider provider) {
    myProvider = provider;
    myTooltips = new HashMap();
  }

  public void invalidate() {
    myTooltips.clear();
  }

  public String getTooltip(MemberInfo member) {
    if(myTooltips.keySet().contains(member)) {
      return (String) myTooltips.get(member);
    }
    String tooltip = myProvider.getTooltip(member);
    myTooltips.put(member, tooltip);
    return tooltip;
  }
}
