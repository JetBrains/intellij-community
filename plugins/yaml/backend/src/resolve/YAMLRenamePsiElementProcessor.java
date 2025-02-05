// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLAnchor;

import java.util.*;
import java.util.function.Predicate;

public class YAMLRenamePsiElementProcessor extends RenamePsiElementProcessor {
  @Override
  public void findExistingNameConflicts(final @NotNull PsiElement element, final @NotNull String newName, final @NotNull MultiMap<PsiElement, String> conflicts) {
    assert element instanceof YAMLAnchor;
    PsiFile file = element.getContainingFile();
    YAMLAnchor anchor = (YAMLAnchor)element;

    int start = anchor.getTextOffset();
    Collection<PsiReference> uses = findReferences(anchor, anchor.getUseScope(), false);
    OptionalInt lastUsePosOpt = uses.stream().mapToInt(r -> r.getElement().getTextOffset()).max();
    int endOfScope = lastUsePosOpt.isPresent() ? lastUsePosOpt.getAsInt() : file.getTextLength();

    List<YAMLAnchor> allAnchors = new ArrayList<>(PsiTreeUtil.collectElementsOfType(file, YAMLAnchor.class));
    int idx = allAnchors.indexOf(anchor);
    if (idx == -1) {
      // some magic
      return;
    }

    // Check such conflict:
    // def1: &prev val1
    // def2: &cur val2 # rename cur -> prev
    // use1: *prev # conflict
    // use2: *cur

    Predicate<YAMLAnchor> hasNewName = a -> a.getName().equals(newName);
    Optional<YAMLAnchor> prevOpt = StreamEx.ofReversed(allAnchors.subList(0, idx)).filter(hasNewName).findFirst();
    if (prevOpt.isPresent()) {
      YAMLAnchor prev = prevOpt.get();
      findReferences(prev, prev.getUseScope(), false).stream()
                          .map(r -> r.getElement())
                          .filter(alias -> start < alias.getTextOffset() && alias.getTextOffset() < endOfScope)
                          .forEach(alias -> conflicts.putValue(alias, YAMLBundle.message("YAMLAnchorRenameProcessor.lost.alias")));
      if (!conflicts.isEmpty()) {
        conflicts.putValue(prev, YAMLBundle.message("YAMLAnchorRenameProcessor.reuse"));
      }
    }

    // Check such conflict:
    // def1: &cur val1 # rename cur -> post
    // def2: &post val2
    // use1: *cur # conflict

    List<YAMLAnchor> anchorsTail = allAnchors.subList(idx + 1, allAnchors.size());
    Optional<YAMLAnchor> postOpt = anchorsTail.stream().filter(hasNewName).findFirst();
    if (postOpt.isPresent()) {
      YAMLAnchor post = postOpt.get();
      uses.stream()
          .map(r -> r.getElement())
          .filter(alias -> post.getTextOffset() < alias.getTextOffset())
          .forEach(alias -> conflicts.putValue(alias, YAMLBundle.message("YAMLAnchorRenameProcessor.lost.alias")));

      if (lastUsePosOpt.isPresent()) {
        anchorsTail.stream()
                   .filter(hasNewName)
                   .filter(anc -> start < anc.getTextOffset() && anc.getTextOffset() < lastUsePosOpt.getAsInt())
                   .forEach(anc -> conflicts.putValue(anc, YAMLBundle.message("YAMLAnchorRenameProcessor.reuse")));
      }
    }
  }

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof YAMLAnchor;
  }
}
