// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public class PropertiesParser implements PsiParser {
  private static final TripleFunction<ASTNode,LighterASTNode,FlyweightCapableTreeStructure<LighterASTNode>,ThreeState>
          MATCH_BY_KEY = (oldNode, newNode, structure) -> {
            if (oldNode.getElementType() == PropertiesElementTypes.PROPERTY) {
              ASTNode oldName = oldNode.findChildByType(PropertiesTokenTypes.KEY_CHARACTERS);
              if (oldName != null) {
                CharSequence oldNameStr = oldName.getChars();
                CharSequence newNameStr = findKeyCharacters(newNode, structure);

                if (!Comparing.equal(oldNameStr, newNameStr)) {
                  return ThreeState.NO;
                }
              }
            }

            return ThreeState.UNSURE;
          };

  private static CharSequence findKeyCharacters(LighterASTNode newNode, FlyweightCapableTreeStructure<LighterASTNode> structure) {
    Ref<LighterASTNode[]> childrenRef = Ref.create(null);
    int childrenCount = structure.getChildren(newNode, childrenRef);
    LighterASTNode[] children = childrenRef.get();

    try {
      for (LighterASTNode aChildren : children) {
        if (aChildren.getTokenType() == PropertiesTokenTypes.KEY_CHARACTERS) {
          return ((LighterASTTokenNode)aChildren).getText();
        }
      }
      return null;
    }
    finally {
      structure.disposeChildren(children, childrenCount);
    }
  }


  @Override
  @NotNull
  public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    doParse(root, builder);
    return builder.getTreeBuilt();
  }

  @NotNull
  public FlyweightCapableTreeStructure<LighterASTNode> parseLight(IElementType root, PsiBuilder builder) {
    doParse(root, builder);
    return builder.getLightTree();
  }

  public void doParse(IElementType root, PsiBuilder builder) {
    builder.putUserData(PsiBuilderImpl.CUSTOM_COMPARATOR, MATCH_BY_KEY);
    final PsiBuilder.Marker rootMarker = builder.mark();
    final PsiBuilder.Marker propertiesList = builder.mark();
    if(builder.eof()){
      propertiesList.setCustomEdgeTokenBinders(WhitespacesBinders.GREEDY_LEFT_BINDER, WhitespacesBinders.GREEDY_RIGHT_BINDER);
    }
    else{
      propertiesList.setCustomEdgeTokenBinders(WhitespacesBinders.GREEDY_LEFT_BINDER, null);
    }

    while (!builder.eof()) {
      Parsing.parseProperty(builder);
    }
    propertiesList.done(PropertiesElementTypes.PROPERTIES_LIST);
    rootMarker.done(root);
  }
}
