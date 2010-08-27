package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public class BuildoutCfgSectionImpl extends BuildoutCfgPsiElementImpl {
  public BuildoutCfgSectionImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Nullable
  public String getHeaderName() {
    BuildoutCfgSectionHeaderImpl header = PsiTreeUtil.findChildOfType(this, BuildoutCfgSectionHeaderImpl.class);
    return header != null ? header.getName() : null;
  }


  public List<BuildoutCfgOptionImpl> getOptions() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, BuildoutCfgOptionImpl.class);
  }

  @Nullable
  public BuildoutCfgOptionImpl findOptionByName(String name) {
    for (BuildoutCfgOptionImpl option : getOptions()) {
      if (name.equals(option.getKey())) {
        return option;
      }
    }
    return null;
  }

  @Nullable
  public String getOptionValue(String name) {
    final BuildoutCfgOptionImpl option = findOptionByName(name);
    if (option != null) {
      return StringUtil.join(option.getValues(), " ");
    }
    return null;
  }

  @Override
  public String toString() {
    return "BuildoutCfgSectionImpl:" + getNode().getElementType().toString();
  }
}
