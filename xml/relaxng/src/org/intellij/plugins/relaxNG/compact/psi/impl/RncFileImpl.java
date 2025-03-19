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

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.intellij.plugins.relaxNG.compact.RncElementTypes;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.compact.RngCompactLanguage;
import org.intellij.plugins.relaxNG.compact.psi.RncDecl;
import org.intellij.plugins.relaxNG.compact.psi.RncFile;
import org.intellij.plugins.relaxNG.compact.psi.RncGrammar;
import org.jetbrains.annotations.NotNull;

public class RncFileImpl extends PsiFileBase implements RncFile, XmlFile {
  private static final TokenSet DECLS = TokenSet.create(RncElementTypes.NS_DECL, RncElementTypes.DATATYPES_DECL);

  public RncFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, RngCompactLanguage.INSTANCE);
  }

  @Override
  public @NotNull FileType getFileType() {
    return RncFileType.getInstance();
  }

  @Override
  public @NotNull XmlDocument getDocument() {
    // this needs to be a seperate child element because of com.intellij.util.xml.impl.ExternalChangeProcessor.visitDocumentChanged()
    final XmlDocument document = findChildByClass(XmlDocument.class);
    assert document != null;
    return document;
  }

  @Override
  public XmlTag getRootTag() {
    return getDocument().getRootTag();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    ASTNode docNode = getDocument().getNode();
    assert docNode != null;
    ASTNode[] nodes = docNode.getChildren(DECLS);
    for (ASTNode node : nodes) {
      if (!processor.execute(node.getPsi(), substitutor)) return false;
    }
    RncGrammar grammar = getGrammar();
    return grammar == null || grammar.processDeclarations(processor, substitutor, lastParent, place);
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return getDocument().add(element);
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return getDocument().addAfter(element, anchor);
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return getDocument().addBefore(element, anchor);
  }

  @Override
  public boolean processElements(PsiElementProcessor processor, PsiElement place) {
    return false;
  }

  @Override
  public @NotNull GlobalSearchScope getFileResolveScope() {
    return ProjectScope.getAllScope(getProject());
  }

  @Override
  public boolean ignoreReferencedElementAccessibility() {
    return false;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ":" + getName();
  }

  @Override
  public RncDecl[] getDeclarations() {
    return ((RncDocument)getDocument()).findChildrenByClass(RncDecl.class);
  }

  @Override
  public RncGrammar getGrammar() {
    final XmlDocument document = getDocument();
    return ((RncDocument)document).getGrammar();
  }
}