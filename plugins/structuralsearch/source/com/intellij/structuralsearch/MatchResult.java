package com.intellij.structuralsearch;

import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;
import com.intellij.psi.PsiElement;

import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.NonNls;

/**
 * Class describing the match result
 */
public abstract class MatchResult {
  @NonNls public static final String LINE_MATCH = "line";
  @NonNls public static final String MULTI_LINE_MATCH = "context";

  public abstract String getMatchImage();

  public abstract SmartPsiPointer getMatchRef();
  public abstract PsiElement getMatch();
  public abstract int getStart();
  public abstract int getEnd();

  public abstract String getName();

  public abstract Iterator<MatchResult> getSons();
  public abstract List<MatchResult> getAllSons();
  public abstract boolean hasSons();
  public abstract boolean isScopeMatch();
  public abstract boolean isMultipleMatch();
}
