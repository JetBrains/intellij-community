/*
 * Created by IntelliJ IDEA.
 * User: spleaner
 * Date: Jun 19, 2007
 * Time: 3:33:15 PM
 */
package com.intellij.xml.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BreadcrumbsInfoProvider {

  public abstract Language[] getLanguages();

  public abstract boolean acceptElement(@NotNull final PsiElement e);

  @NotNull
  public abstract String getElementInfo(@NotNull final PsiElement e);

  @Nullable
  public abstract String getElementTooltip(@NotNull final PsiElement e);
}