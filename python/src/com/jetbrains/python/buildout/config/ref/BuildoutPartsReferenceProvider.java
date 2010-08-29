package com.jetbrains.python.buildout.config.ref;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.django.facet.DjangoFacet;
import com.jetbrains.django.ref.DjangoDirectoryReferenceSet;
import com.jetbrains.django.ref.DjangoFSItemReference;
import com.jetbrains.django.util.DjangoStringUtil;
import com.jetbrains.django.util.DjangoUtil;
import com.jetbrains.django.util.PsiUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.buildout.BuildoutFacet;
import com.jetbrains.python.buildout.config.psi.BuildoutPsiUtil;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgValueLine;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class BuildoutPartsReferenceProvider extends PsiReferenceProvider {

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    Module module = ModuleUtil.findModuleForPsiElement(element);
    BuildoutFacet buildoutFacet = BuildoutFacet.getInstance(module);
    if (buildoutFacet != null) {
      if (element instanceof BuildoutCfgValueLine && BuildoutPsiUtil.isInBuildoutSection(element) && BuildoutPsiUtil.isAssignedTo(element, "parts")) {
        BuildoutCfgValueLine line = (BuildoutCfgValueLine)element;
        String[] partNames = line.getText().split("\\s");
        PsiReference[] refs = new BuildoutPartReference[partNames.length];
        int i = 0;
        for (String partName: partNames) {
           refs[i++] = new BuildoutPartReference(element, partName);
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
