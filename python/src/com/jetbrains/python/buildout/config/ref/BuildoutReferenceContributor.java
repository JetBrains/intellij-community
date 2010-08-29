package com.jetbrains.python.buildout.config.ref;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.jetbrains.django.lang.template.psi.impl.DjangoMemberExpressionImpl;
import com.jetbrains.django.lang.template.psi.impl.DjangoMemberNameImpl;
import com.jetbrains.django.lang.template.psi.impl.DjangoStringLiteralImpl;
import com.jetbrains.django.lang.template.ref.DjangoIncludeExtendsTagReferenceProvider;
import com.jetbrains.django.ref.*;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgValueLine;
import com.jetbrains.python.psi.PyStringLiteralExpression;

/**
 * @author traff
 *
 */
public class BuildoutReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(BuildoutCfgValueLine.class),
                                        new BuildoutPartsReferenceProvider());
  }
}
