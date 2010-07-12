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

import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.*;
import org.intellij.plugins.relaxNG.compact.psi.util.EscapeUtil;
import org.intellij.plugins.relaxNG.compact.psi.util.RenameUtil;
import org.intellij.plugins.relaxNG.model.Define;
import org.intellij.plugins.relaxNG.model.resolve.DefinitionResolver;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 13.08.2007
 */
class PatternReference extends PsiReferenceBase.Poly<RncRef> implements Function<Define, ResolveResult>,
        QuickFixProvider<PatternReference>, EmptyResolveMessageProvider {

  public PatternReference(RncRef ref) {
    super(ref);
  }

  public TextRange getRangeInElement() {
    final ASTNode node = findNameNode();
    if (node == null) return TextRange.from(0, 0);
    final int offset = myElement.getTextOffset();
    return TextRange.from(offset - myElement.getTextRange().getStartOffset(), node.getTextLength());
  }

  private ASTNode findNameNode() {
    final ASTNode node = myElement.getNode();
    assert node != null;
    return node.findChildByType(RncTokenTypes.IDENTIFIERS);
  }

  @Nullable
  public PsiElement resolve() {
    final ResolveResult[] results = multiResolve(false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    final RncGrammar scope = getScope();
    if (scope == null) {
      return ResolveResult.EMPTY_ARRAY;
    }

    final Set<Define> set = DefinitionResolver.resolve(scope, getCanonicalText());
    if (set == null || set.size() == 0) return ResolveResult.EMPTY_ARRAY;

    return ContainerUtil.map2Array(set, ResolveResult.class, this);
  }

  public ResolveResult fun(Define rncDefine) {
    final PsiElement element = rncDefine.getPsiElement();
    return element != null ? new PsiElementResolveResult(element) : new ResolveResult() {
      @Nullable
      public PsiElement getElement() {
        return null;
      }
      public boolean isValidResult() {
        return false;
      }
    };
  }

  @Nullable
  protected RncGrammar getScope() {
    return PsiTreeUtil.getParentOfType(myElement, RncGrammar.class, true, PsiFile.class);
  }

  public String getCanonicalText() {
    final ASTNode node = findNameNode();
    return node != null ? EscapeUtil.unescapeText(node) : "";
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final ASTNode newNode = RenameUtil.createIdentifierNode(getElement().getManager(), newElementName);

    final ASTNode nameNode = findNameNode();
    nameNode.getTreeParent().replaceChild(nameNode, newNode);
    return getElement();
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public Object[] getVariants() {
    final RncGrammar scope = getScope();
    if (scope == null) {
      return ResolveResult.EMPTY_ARRAY;
    }

    final Map<String, Set<Define>> map = DefinitionResolver.getAllVariants(scope);
    if (map == null || map.size() == 0) return ArrayUtil.EMPTY_OBJECT_ARRAY;

    return ContainerUtil.mapNotNull(map.values(), new Function<Set<Define>, Object>() {
      public Object fun(Set<Define> defines) {
        return defines.size() == 0 ? null : defines.iterator().next().getPsiElement();
      }
    }).toArray();
  }

  public boolean isSoft() {
    return false;
  }

  public String getUnresolvedMessagePattern() {
    return "Unresolved pattern reference ''{0}''";
  }

  public void registerQuickfix(HighlightInfo info, final PatternReference reference) {
    if (reference.getScope() == null) {
      return;
    }
    QuickFixAction.registerQuickFixAction(info, new CreatePatternFix(reference));
  }

  static class CreatePatternFix implements IntentionAction {
    private final PatternReference myReference;

    public CreatePatternFix(PatternReference reference) {
      myReference = reference;
    }

    @NotNull
    public String getText() {
      return "Create Pattern '" + myReference.getCanonicalText() + "'";
    }

    @NotNull
    public String getFamilyName() {
      return "Create Pattern";
    }

    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return myReference.getElement().isValid() && myReference.getScope() != null;
    }

    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      final RncFile rncfile = (RncFile)PsiFileFactory.getInstance(myReference.getElement().getProject()).createFileFromText("dummy.rnc", RncFileType.getInstance(), "dummy = xxx");

      final RncGrammar grammar = rncfile.getGrammar();
      assert grammar != null;

      final RncDefine def = (RncDefine)grammar.getFirstChild();

      final RncGrammar scope = myReference.getScope();
      assert scope != null;

      assert def != null;
      final RncDefine e = (RncDefine)scope.add(def);

      // ensures proper escaping (start -> \start)
      def.setName(myReference.getCanonicalText());

      final SmartPsiElementPointer<RncDefine> p = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(e);

      final ASTNode blockNode = e.getParent().getNode();
      assert blockNode != null;

      final ASTNode newNode = e.getNode();
      assert newNode != null;

      e.getManager().getCodeStyleManager().reformatNewlyAddedElement(blockNode, newNode);

      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());

      final RncDefine d = p.getElement();
      assert d != null;

      final RncElement definition = d.getPattern();
      assert definition != null;

      final int offset = definition.getTextRange().getStartOffset();

      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      editor.getDocument().deleteString(offset, definition.getTextRange().getEndOffset());
    }

    public boolean startInWriteAction() {
      return true;
    }
  }
}
