package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;
import org.jetbrains.annotations.NonNls;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Class describing the match result
 */
public final class MatchResultImpl extends MatchResult {
  private String name;
  private SmartPsiPointer matchRef;
  private int start;
  private int end = -1;
  private String matchImage;
  private List<MatchResult> matches;
  private MatchResult parent;
  private boolean target;

  @NonNls public static final String DEFAULT_NAME2 = "end of context match";
  @NonNls public static final String DEFAULT_NAME = "start of context match";
  private boolean myScopeMatch;
  private boolean myMultipleMatch;
  @NonNls private static final String NULL = "null";
  private MatchResultImpl myContext;

  MatchResultImpl() {
  }

  public MatchResultImpl(String name, String image, SmartPsiPointer ref, boolean target) {
    this(name,image,ref,0,-1,target);
  }

  public MatchResultImpl(String name, String image, SmartPsiPointer ref, int start, int end,boolean target) {
    matchRef = ref;
    this.name = name;
    matchImage = image;
    this.target = target;
    this.start = start;
    this.end = end;
  }

  public String getMatchImage() {
    if (matchImage==null) {
      matchImage = NULL;
    }
    return matchImage;
  }

  public void setParent(MatchResult parent) {
    this.parent = parent;
  }

  public SmartPsiPointer getMatchRef() {
    return matchRef;
  }

  public PsiElement getMatch() {
    return matchRef.getElement();
  }

  public void setMatchRef(SmartPsiPointer matchStart) {
    matchRef = matchStart;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<MatchResult> getMatches() {
    if (matches==null) matches = new LinkedList<MatchResult>();
    return matches;
  }

  public List<MatchResult> getAllSons() {
    return getMatches();
  }

  public boolean hasSons() {
    return matches!=null;
  }

  public boolean isScopeMatch() {
    return myScopeMatch;
  }

  public boolean isMultipleMatch() {
    return myMultipleMatch;
  }

  public void clear() {
    if (matchRef!=null) {
      matchRef.clear();
      matchRef = null;
    }

    if (matches!=null) {
      for (final MatchResult matche : matches) {
        ((MatchResultImpl)matche).clear();
      }
      matches = null;
    }

    name = null;
    matchImage = null;
  }

  public void clearMatches() {
    matches = null;
  }

  public void setScopeMatch(final boolean scopeMatch) {
    myScopeMatch = scopeMatch;
  }

  public void setMultipleMatch(final boolean multipleMatch) {
    myMultipleMatch = multipleMatch;
  }

  public MatchResultImpl findSon(String name) {
    if (matches!=null) {
      // @todo this could be performance bottleneck, replace with hash lookup!
      for (final MatchResult matche : matches) {
        final MatchResultImpl res = (MatchResultImpl)matche;

        if (name.equals(res.getName())) {
          return res;
        }
      }
    }
    return null;
  }

  public void removeSon(String typedVar) {
    if (matches == null) return;

    // @todo this could be performance bottleneck, replace with hash lookup!
    for(Iterator i=matches.iterator();i.hasNext();) {
      final MatchResultImpl res = (MatchResultImpl)i.next();
      if (typedVar.equals(res.getName())) {
        i.remove();
        break;
      }
    }
  }

  public void addSon(MatchResultImpl result) {
    getMatches().add(result);
  }

  public void setMatchImage(String matchImage) {
    this.matchImage = matchImage;
  }

  boolean isTarget() {
    return target;
  }

  public void setTarget(boolean target) {
    this.target = target;
  }

  public boolean isMatchImageNull() {
    return matchImage==null;
  }

  public int getStart() {
    return start;
  }

  public void setStart(int start) {
    this.start = start;
  }

  public int getEnd() {
    return end;
  }

  public void setEnd(int end) {
    this.end = end;
  }

  public void setContext(final MatchResultImpl context) {
    myContext = context;
  }

  public MatchResultImpl getContext() {
    return myContext;
  }
}

