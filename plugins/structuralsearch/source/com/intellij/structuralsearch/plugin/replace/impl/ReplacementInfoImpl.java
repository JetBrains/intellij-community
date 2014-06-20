package com.intellij.structuralsearch.plugin.replace.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 03.12.2004
 * Time: 21:33:53
 * To change this template use File | Settings | File Templates.
 */
public class ReplacementInfoImpl extends ReplacementInfo {
  List<SmartPsiElementPointer> matchesPtrList;
  String result;
  MatchResult matchResult;
  Map<String,MatchResult> variableMap;
  Map<PsiElement,String> elementToVariableNameMap;

  public String getReplacement() {
    return result;
  }

  public void setReplacement(String replacement) {
    result = replacement;
  }

  @Nullable
  @Override
  public PsiElement getMatch(int index) {
    return matchesPtrList.get(index).getElement();
  }

  @Override
  public int getMatchesCount() {
    return matchesPtrList.size();
  }

  public Map<String, MatchResult> getVariableMap() {
    return variableMap;
  }

  public MatchResult getMatchResult() {
    return matchResult;
  }
}
