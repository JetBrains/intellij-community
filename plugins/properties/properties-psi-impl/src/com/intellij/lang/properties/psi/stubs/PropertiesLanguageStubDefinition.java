// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.parsing.PropertiesParser;
import com.intellij.lang.properties.parsing.PropertiesParserDefinition;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.LightLanguageStubDefinition;
import com.intellij.psi.stubs.LightStubBuilder;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;

public class PropertiesLanguageStubDefinition implements LightLanguageStubDefinition {
  @Override
  public int getStubVersion() {
    return 0;
  }

  @Override
  public @NotNull StubBuilder getBuilder() {
    return new LightStubBuilder();
  }

  @Override
  public @NotNull FlyweightCapableTreeStructure<@NotNull LighterASTNode> parseContentsLight(@NotNull ASTNode chameleon) {
    PsiElement psi = chameleon.getPsi();
    assert psi != null : "Bad chameleon: " + chameleon;

    Project project = psi.getProject();
    PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
    PsiBuilder builder = factory.createBuilder(project, chameleon);
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(PropertiesLanguage.INSTANCE);
    assert parserDefinition != null : this;
    PropertiesParser parser = new PropertiesParser();
    return parser.parseLight(PropertiesParserDefinition.FILE_ELEMENT_TYPE, builder);
  }
}