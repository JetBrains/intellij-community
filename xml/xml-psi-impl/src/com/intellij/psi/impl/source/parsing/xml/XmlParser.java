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

/*
 * @author max
 */
package com.intellij.psi.impl.source.parsing.xml;

import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ThreeState;
import com.intellij.util.TripleFunction;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;

public class XmlParser implements PsiParser {
  // tries to match an old and new XmlTag by name
  private static final TripleFunction<ASTNode,LighterASTNode,FlyweightCapableTreeStructure<LighterASTNode>,ThreeState>
    REPARSE_XML_TAG_BY_NAME = new TripleFunction<ASTNode, LighterASTNode, FlyweightCapableTreeStructure<LighterASTNode>, ThreeState>() {
      @Override
      public ThreeState fun(ASTNode oldNode,
                            LighterASTNode newNode,
                            FlyweightCapableTreeStructure<LighterASTNode> structure) {
        if (oldNode instanceof XmlTag && newNode.getTokenType() == XmlElementType.XML_TAG) {
          String oldName = ((XmlTag)oldNode).getName();
          Ref<LighterASTNode[]> childrenRef = Ref.create(null);
          int count = structure.getChildren(newNode, childrenRef);
          if (count < 3) return ThreeState.UNSURE;
          LighterASTNode[] children = childrenRef.get();
          if (children[0].getTokenType() != XmlTokenType.XML_START_TAG_START) return ThreeState.UNSURE;
          if (children[1].getTokenType() != XmlTokenType.XML_NAME) return ThreeState.UNSURE;
          if (children[2].getTokenType() != XmlTokenType.XML_TAG_END) return ThreeState.UNSURE;
          LighterASTTokenNode name = (LighterASTTokenNode)children[1];
          CharSequence newName = name.getText();
          if (!Comparing.equal(oldName, newName)) return ThreeState.NO;
        }

        return ThreeState.UNSURE;
      }
    };

  @Override
  @NotNull
  public ASTNode parse(final IElementType root, final PsiBuilder builder) {
    builder.enforceCommentTokens(TokenSet.EMPTY);
    builder.putUserDataUnprotected(PsiBuilderImpl.CUSTOM_COMPARATOR, REPARSE_XML_TAG_BY_NAME);
    final PsiBuilder.Marker file = builder.mark();
    new XmlParsing(builder).parseDocument();
    file.done(root);
    return builder.getTreeBuilt();
  }
}
