package com.intellij.structuralsearch.impl.matcher;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class TokenBasedMatchResult extends MatchResult {
  private final MatchResult myDelegate;
  private TextRange myTextRange;

  private RangeMarker myRangeMarker;

  public TokenBasedMatchResult(MatchResult delegate, TextRange textRange) {
    myDelegate = delegate;
    myTextRange = textRange;
  }

  public void setRangeMarker(RangeMarker rangeMarker) {
    myRangeMarker = rangeMarker;
  }

  public RangeMarker getRangeMarker() {
    return myRangeMarker;
  }

  @Nullable
  public TextRange getTextRangeInFile() {
    return myTextRange;
  }

  public void setTextRangeInFile(TextRange textRange) {
    myTextRange = textRange;
  }

  @Override
  public String getMatchImage() {
    return myDelegate.getMatchImage();
  }

  @Override
  public SmartPsiPointer getMatchRef() {
    return myDelegate.getMatchRef();
  }

  @Override
  public PsiElement getMatch() {
    return myDelegate.getMatch();
  }

  @Override
  public int getStart() {
    return myDelegate.getStart();
  }

  @Override
  public int getEnd() {
    return myDelegate.getEnd();
  }

  @Override
  public String getName() {
    return myDelegate.getName();
  }

  @Override
  public List<MatchResult> getAllSons() {
    return myDelegate.getAllSons();
  }

  @Override
  public boolean hasSons() {
    return myDelegate.hasSons();
  }

  @Override
  public boolean isScopeMatch() {
    return myDelegate.isScopeMatch();
  }

  @Override
  public boolean isMultipleMatch() {
    return myDelegate.isMultipleMatch();
  }
}
