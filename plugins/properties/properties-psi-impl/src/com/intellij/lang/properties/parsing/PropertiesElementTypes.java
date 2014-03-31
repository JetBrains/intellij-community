/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.PropertiesFileType;
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

  IStubElementType PROPERTIES_LIST = new PropertyListStubElementType();
  TokenSet PROPERTIES = TokenSet.create(PROPERTY);
}
