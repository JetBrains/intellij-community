// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.ILightStubFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.diff.FlyweightCapableTreeStructure;

/**
 * @author max
 */
public interface PropertiesElementTypes {
  PropertiesLanguage LANG = PropertiesLanguage.INSTANCE;

  ILightStubFileElementType FILE = new ILightStubFileElementType(LANG) {
    public FlyweightCapableTreeStructure<LighterASTNode> parseContentsLight(ASTNode chameleon) {
      PsiElement psi = chameleon.getPsi();
      assert (psi != null) : ("Bad chameleon: " + chameleon);

      Project project = psi.getProject();
      PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
      PsiBuilder builder = factory.createBuilder(project, chameleon);
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(getLanguage());
      assert (parserDefinition != null) : this;
      PropertiesParser parser = new PropertiesParser();
      return parser.parseLight(this, builder);
    }
  };
  IStubElementType PROPERTY = new PropertyStubElementType();

  PropertyListStubElementType PROPERTIES_LIST = new PropertyListStubElementType();
  TokenSet PROPERTIES = TokenSet.create(PROPERTY);
}
