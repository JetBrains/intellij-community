/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.xml.util.XmlUtil;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.RncFile;
import org.intellij.plugins.relaxNG.compact.psi.RncFileReference;
import org.intellij.plugins.relaxNG.compact.psi.util.EscapeUtil;
import org.intellij.plugins.relaxNG.references.FileReferenceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 29.08.2007
 */
abstract class RncFileReferenceImpl extends RncElementImpl implements RncFileReference {
  public RncFileReferenceImpl(ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  @SuppressWarnings({ "SSBasedInspection" })
  public PsiReference[] getReferences() {
    final ASTNode literal = getNode().findChildByType(RncTokenTypes.LITERAL);
    if (literal == null) return PsiReference.EMPTY_ARRAY;

    final String s = literal.getText();
    final FileReferenceSet set =
            new FileReferenceSet(s.substring(1, s.length() - 1), this,
                    getReferenceRange().getStartOffset(),
                    null, true, false);

    return FileReferenceUtil.restrict(set, FileReferenceUtil.byType(RncFileType.getInstance()));
  }

  public RncFile getReferencedFile() {
    final String href = getFileReference();
    if (href != null) {
      final PsiFile file = XmlUtil.findRelativeFile(href, getContainingFile());
      if (file instanceof RncFile) {
        return (RncFile)file;
      }
    }
    return null;
  }

  @Nullable
  public String getFileReference() {
    final ASTNode element = getNode().findChildByType(RncTokenTypes.LITERAL);
    if (element == null) return null;
    final String s = EscapeUtil.unescapeText(element);
    return s.substring(1, s.length() - 1);
  }

  public TextRange getReferenceRange() {
    final ASTNode literal = getNode().findChildByType(RncTokenTypes.LITERAL);
    if (literal == null) return TextRange.from(0, 0);
    final int startInElement = literal.getStartOffset() - getTextOffset() + 1;
    return TextRange.from(startInElement, literal.getTextLength() - 2);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    final FollowFileHint hint = processor.getHint(FollowFileHint.KEY);
    final RncFile file = getReferencedFile();
    if (file != null && hint != null && hint.doFollow(file)) {
      file.processDeclarations(processor, substitutor, lastParent, place);
    }
    return true;
  }
}
