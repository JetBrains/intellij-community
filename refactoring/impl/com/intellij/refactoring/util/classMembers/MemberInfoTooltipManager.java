package com.intellij.refactoring.util.classMembers;



import com.intellij.util.containers.HashMap;



/**

 * @author dsl

 */

public class MemberInfoTooltipManager {

  private final HashMap<MemberInfo, String> myTooltips = new HashMap<MemberInfo, String>();

  private final TooltipProvider myProvider;



  public interface TooltipProvider {

    String getTooltip(MemberInfo memberInfo);

  }



  public MemberInfoTooltipManager(TooltipProvider provider) {

    myProvider = provider;

  }



  public void invalidate() {

    myTooltips.clear();

  }



  public String getTooltip(MemberInfo member) {

    if(myTooltips.keySet().contains(member)) {

      return myTooltips.get(member);

    }

    String tooltip = myProvider.getTooltip(member);

    myTooltips.put(member, tooltip);

    return tooltip;

  }

}

