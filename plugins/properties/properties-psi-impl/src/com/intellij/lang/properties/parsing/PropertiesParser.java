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

import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ThreeState;
import com.intellij.util.TripleFunction;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class PropertiesParser implements PsiParser {
  private static final TripleFunction<ASTNode,LighterASTNode,FlyweightCapableTreeStructure<LighterASTNode>,ThreeState>
          MATCH_BY_KEY = new TripleFunction<ASTNode, LighterASTNode, FlyweightCapableTreeStructure<LighterASTNode>, ThreeState>() {
    @Override
    public ThreeState fun(ASTNode oldNode,
                          LighterASTNode newNode,
                          FlyweightCapableTreeStructure<LighterASTNode> structure) {
      if (oldNode.getElementType() == PropertiesElementTypes.PROPERTY) {
        ASTNode oldName = oldNode.findChildByType(PropertiesTokenTypes.KEY_CHARACTERS);
        if (oldName != null) {
          CharSequence oldNameStr = oldName.getChars();
          CharSequence newNameStr = findKeyCharacters(newNode, structure);

          if (oldNameStr != null && !Comparing.equal(oldNameStr, newNameStr)) {
            return ThreeState.NO;
          }
        }
      }

      return ThreeState.UNSURE;
    }
  };

  private static CharSequence findKeyCharacters(LighterASTNode newNode, FlyweightCapableTreeStructure<LighterASTNode> structure) {
    Ref<LighterASTNode[]> childrenRef = Ref.create(null);
    int childrenCount = structure.getChildren(newNode, childrenRef);
    LighterASTNode[] children = childrenRef.get();

    try {
      for (int i = 0; i < children.length; ++i) {
        if (children[i].getTokenType() == PropertiesTokenTypes.KEY_CHARACTERS)
          return ((LighterASTTokenNode) children[i]).getText();
      }
      return null;
    }
    finally {
      structure.disposeChildren(children, childrenCount);
    }
  }


  @NotNull
  public ASTNode parse(IElementType root, PsiBuilder builder) {
    doParse(root, builder);
    return builder.getTreeBuilt();
  }

  @NotNull
  public FlyweightCapableTreeStructure<LighterASTNode> parseLight(IElementType root, PsiBuilder builder) {
    doParse(root, builder);
    return builder.getLightTree();
  }

  public void doParse(IElementType root, PsiBuilder builder) {
    builder.putUserDataUnprotected(PsiBuilderImpl.CUSTOM_COMPARATOR, MATCH_BY_KEY);
    final PsiBuilder.Marker rootMarker = builder.mark();
    final PsiBuilder.Marker propertiesList = builder.mark();
    while (!builder.eof()) {
      Parsing.parseProperty(builder);
    }
    propertiesList.done(PropertiesElementTypes.PROPERTIES_LIST);
    rootMarker.done(root);
  }
}
