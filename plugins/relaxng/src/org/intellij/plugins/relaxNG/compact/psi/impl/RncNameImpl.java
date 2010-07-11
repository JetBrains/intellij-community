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

import org.intellij.plugins.relaxNG.compact.RncElementTypes;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.*;
import org.intellij.plugins.relaxNG.compact.psi.util.EscapeUtil;
import org.intellij.plugins.relaxNG.compact.psi.util.RenameUtil;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 14.08.2007
 */
public class RncNameImpl extends RncElementImpl implements RncName, PsiReference,
        EmptyResolveMessageProvider, QuickFixProvider<RncNameImpl> {
  private enum Kind {
    NAMESPACE, DATATYPES
  }
  private final Kind myKind;

  public RncNameImpl(ASTNode node) {
    super(node);

    final IElementType parent = node.getTreeParent().getElementType();
    if (parent == RncElementTypes.DATATYPE_PATTERN) {
      myKind = Kind.DATATYPES;
    } else {
      myKind = Kind.NAMESPACE;
    }
  }

  @Nullable
  public String getPrefix() {
    final String[] parts = EscapeUtil.unescapeText(getNode()).split(":", 2);
    return parts.length == 2 ? parts[0] : null;
  }

  @NotNull
  public String getLocalPart() {
    final String[] parts = EscapeUtil.unescapeText(getNode()).split(":", 2);
    return parts.length == 1 ? parts[0] : parts[1];
  }

  @Override
  public void accept(@NotNull RncElementVisitor visitor) {
    visitor.visitName(this);
  }

  @Override
  public PsiReference getReference() {
    return getPrefix() == null ? null : this;
  }

  public PsiElement getElement() {
    return this;
  }

  public TextRange getRangeInElement() {
    return TextRange.from(0, getText().indexOf(':'));
  }

  @Nullable
  public PsiElement resolve() {
    final MyResolver resolver = new MyResolver(getPrefix(), myKind);
    getContainingFile().processDeclarations(resolver, ResolveState.initial(), this, this);
    return resolver.getResult();
  }

  public String getCanonicalText() {
    return getRangeInElement().substring(getText());
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final ASTNode node = getNode();
    final ASTNode child = RenameUtil.createPrefixedNode(getManager(), newElementName, getLocalPart());
    node.getTreeParent().replaceChild(node, child);
    return child.getPsi();
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public boolean isReferenceTo(PsiElement element) {
    return element instanceof RncElement && Comparing.equal(resolve(), element);
  }

  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    final String prefix = getPrefix();
    return "xsd".equals(prefix) || "xml".equals(prefix);
  }

  public String getUnresolvedMessagePattern() {
    return "Unresolved namespace prefix ''{0}''";
  }

  public void registerQuickfix(HighlightInfo info, final RncNameImpl reference) {
    if (reference.getPrefix() == null) return; // huh?

    QuickFixAction.registerQuickFixAction(info, new CreateDeclFix(reference));
  }

  private static class MyResolver extends BaseScopeProcessor {
    private final String myPrefix;
    private final Kind myKind;
    private PsiElement myResult;

    public MyResolver(String prefix, Kind kind) {
      myPrefix = prefix;
      myKind = kind;
    }

    public boolean execute(PsiElement element, ResolveState substitutor) {
      final ASTNode node = element.getNode();
      if (node == null) return true;

      if (!(element instanceof RncDecl)) {
        return false;
      }

      final IElementType type = node.getElementType();
      if (myKind == Kind.NAMESPACE && type == RncElementTypes.NS_DECL) {
        if (checkDecl(element)) return false;
      } else if (myKind == Kind.DATATYPES && type == RncElementTypes.DATATYPES_DECL) {
        if (checkDecl(element)) return false;
      }

      return true;
    }

    private boolean checkDecl(PsiElement element) {
      if (myPrefix.equals(((RncDecl)element).getPrefix())) {
        myResult = element;
        return true;
      }
      return false;
    }

    public PsiElement getResult() {
      return myResult;
    }
  }

  public static class CreateDeclFix implements IntentionAction {
    private final RncNameImpl myReference;

    public CreateDeclFix(RncNameImpl reference) {
      myReference = reference;
    }

    @NotNull
      public String getText() {
      return getFamilyName() + " '" + myReference.getPrefix() + "'";
    }

    @NotNull
      public String getFamilyName() {
      return "Create " + myReference.myKind.name().toLowerCase() + " declaration";
    }

    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return myReference.isValid();
    }

    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      final String prefix = myReference.getPrefix();
      final RncFile psiFile = (RncFile)PsiFileFactory.getInstance(myReference.getProject()).createFileFromText("dummy.rnc", myReference.myKind.name().toLowerCase() + " " + prefix + " = \"###\"");
      final RncFile rncFile = (RncFile)myReference.getContainingFile();
      final RncDecl[] declarations = rncFile.getDeclarations();
      final RncDecl decl = psiFile.getDeclarations()[0];
      final RncDecl e;
      if (declarations.length > 0) {
        e = (RncDecl)rncFile.addAfter(decl, declarations[declarations.length - 1]);
      } else {
        final RncGrammar rncGrammar = rncFile.getGrammar();
        if (rncGrammar != null) {
          e = (RncDecl)rncFile.addBefore(decl, rncGrammar);
        } else {
          e = (RncDecl)rncFile.add(decl);
        }
      }

      final ASTNode blockNode = e.getParent().getNode();
      assert blockNode != null;

      final ASTNode newNode = e.getNode();
      assert newNode != null;

      e.getManager().getCodeStyleManager().reformatNewlyAddedElement(blockNode, newNode);

      final SmartPsiElementPointer<RncDecl> p = SmartPointerManager.getInstance(project).createLazyPointer(e);
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());

      final RncDecl d = p.getElement();
      assert d != null;

      final PsiElement literal = d.getLastChild();
      assert literal != null;

      final ASTNode literalNode = literal.getNode();
      assert literalNode != null;

      assert literalNode.getElementType() == RncTokenTypes.LITERAL;

      final int offset = literal.getTextRange().getStartOffset();
      editor.getDocument().deleteString(literal.getTextRange().getStartOffset(), literal.getTextRange().getEndOffset());

      final TemplateManager manager = TemplateManager.getInstance(project);
      final Template t = manager.createTemplate("", "");
      t.addTextSegment("\"");
      final Expression expression = new Expression() {
        public Result calculateResult(ExpressionContext context) {
          return new TextResult("");
        }

        public Result calculateQuickResult(ExpressionContext context) {
          return calculateResult(context);
        }

        public LookupItem[] calculateLookupItems(ExpressionContext context) {
          return LookupItem.EMPTY_ARRAY;
        }
      };
      t.addVariable("uri", expression, expression, true);
      t.addTextSegment("\"");
      t.addEndVariable();

      editor.getCaretModel().moveToOffset(offset);
      manager.startTemplate(editor, t);
    }

    public boolean startInWriteAction() {
      return true;
    }
  }
}
