// This is a generated file. Not intended for manual editing.
package com.jetbrains.python.requirements.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface Uri extends PsiElement {

  @Nullable
  Fragment getFragment();

  @Nullable
  HierPart getHierPart();

  @Nullable
  Query getQuery();

  @NotNull
  Scheme getScheme();

}
