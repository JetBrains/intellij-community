/*
 * @author max
 */
package com.intellij.psi.impl.source;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class JavaDummyHolderFactory implements HolderFactory {
  public DummyHolder createHolder(@NotNull final PsiManager manager, final TreeElement contentElement, final PsiElement context) {
    return new JavaDummyHolder(manager, contentElement, context);
  }

  public DummyHolder createHolder(@NotNull final PsiManager manager,
                                  final TreeElement contentElement, final PsiElement context, final CharTable table) {
    return new JavaDummyHolder(manager, contentElement, context, table);
  }

  public DummyHolder createHolder(@NotNull final PsiManager manager, final PsiElement context) {
    return new JavaDummyHolder(manager, context);
  }

  public DummyHolder createHolder(@NotNull final PsiManager manager, final PsiElement context, final CharTable table) {
    return new JavaDummyHolder(manager, context, table);
  }

  public DummyHolder createHolder(@NotNull final PsiManager manager, final CharTable table, final Language language) {
    return new JavaDummyHolder(manager, table);
  }

  public DummyHolder createHolder(@NotNull final PsiManager manager, final CharTable table, final boolean validity) {
    return new JavaDummyHolder(manager, table, validity);
  }
}