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
package org.intellij.plugins.relaxNG;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.RncFileReference;
import org.intellij.plugins.relaxNG.compact.psi.util.RenameUtil;
import org.jetbrains.annotations.NotNull;

/**
* @author peter
*/
public class RncFileReferenceManipulator extends AbstractElementManipulator<RncFileReference> {
  @Override
  public RncFileReference handleContentChange(@NotNull RncFileReference element, @NotNull TextRange range, String newContent) throws
                                                                                                            IncorrectOperationException {
    final ASTNode node = element.getNode();
    assert node != null;

    final ASTNode literal = node.findChildByType(RncTokenTypes.LITERAL);
    if (literal != null) {
      assert range.equals(element.getReferenceRange());
      final PsiManager manager = element.getManager();
      final ASTNode newChild = RenameUtil.createLiteralNode(manager, newContent);
      literal.getTreeParent().replaceChild(literal, newChild);
    }
    return element;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement(@NotNull RncFileReference element) {
    return element.getReferenceRange();
  }
}
