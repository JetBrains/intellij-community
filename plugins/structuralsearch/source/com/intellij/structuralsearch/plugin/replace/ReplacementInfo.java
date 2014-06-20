package com.intellij.structuralsearch.plugin.replace;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 03.12.2004
 * Time: 21:33:53
 * To change this template use File | Settings | File Templates.
 */
public abstract class ReplacementInfo {
  public abstract String getReplacement();

  public abstract void setReplacement(String replacement);

  @Nullable
  public abstract PsiElement getMatch(int index);

  public abstract int getMatchesCount();
}
