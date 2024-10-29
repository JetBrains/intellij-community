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

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveState;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.psi.XmlPsiBundle;
import org.intellij.plugins.relaxNG.RelaxngBundle;
import org.intellij.plugins.relaxNG.compact.RncElementTypes;
import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.*;
import org.intellij.plugins.relaxNG.compact.psi.util.EscapeUtil;
import org.intellij.plugins.relaxNG.compact.psi.util.RenameUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RncNameImpl extends RncElementImpl implements RncName, PsiReference,
        EmptyResolveMessageProvider, LocalQuickFixProvider {

  private enum Kind {
    NAMESPACE, DATATYPES
  }

  public RncNameImpl(ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable String getPrefix() {
    final String[] parts = EscapeUtil.unescapeText(getNode()).split(":", 2);
    return parts.length == 2 ? parts[0] : null;
  }

  @Override
  public @NotNull String getLocalPart() {
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

  @Override
  public @NotNull PsiElement getElement() {
    return this;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return TextRange.from(0, getText().indexOf(':'));
  }

  @Override
  public @Nullable PsiElement resolve() {
    final MyResolver resolver = new MyResolver(getPrefix(), getKind());
    getContainingFile().processDeclarations(resolver, ResolveState.initial(), this, this);
    return resolver.getResult();
  }

  private Kind getKind() {
    final IElementType parent = getNode().getTreeParent().getElementType();
    if (parent == RncElementTypes.DATATYPE_PATTERN) {
      return Kind.DATATYPES;
    } else {
      return Kind.NAMESPACE;
    }
  }

  @Override
  public @NotNull String getCanonicalText() {
    return getRangeInElement().substring(getText());
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    final ASTNode node = getNode();
    final ASTNode child = RenameUtil.createPrefixedNode(getManager(), newElementName, getLocalPart());
    node.getTreeParent().replaceChild(node, child);
    return child.getPsi();
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return element instanceof RncElement && Comparing.equal(resolve(), element);
  }

  @Override
  public boolean isSoft() {
    final String prefix = getPrefix();
    return "xsd".equals(prefix) || "xml".equals(prefix);
  }

  @Override
  public @NotNull String getUnresolvedMessagePattern() {
    //The format substitution is performed at the call site
    //noinspection UnresolvedPropertyKey
    return RelaxngBundle.message("relaxng.annotator.unresolved-namespace-prefix");
  }

  @Override
  public @NotNull LocalQuickFix @Nullable [] getQuickFixes() {
    if (getPrefix() != null) {
      return new LocalQuickFix[] { new CreateDeclFix(this) };
    }
    return LocalQuickFix.EMPTY_ARRAY;
  }

  private static class MyResolver implements PsiScopeProcessor {
    private final String myPrefix;
    private final Kind myKind;
    private PsiElement myResult;

    MyResolver(String prefix, Kind kind) {
      myPrefix = prefix;
      myKind = kind;
    }

    @Override
    public boolean execute(@NotNull PsiElement element, @NotNull ResolveState substitutor) {
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

  public static class CreateDeclFix implements LocalQuickFix {
    private final @NotNull @Nls String myName;

    public CreateDeclFix(RncNameImpl reference) {
      myName = RelaxngBundle.message("relaxng.quickfix.create-declaration.name", StringUtil.toLowerCase(reference.getKind().name()),
                                     reference.getPrefix());
    }

    @Override
    public @NotNull String getName() {
      return myName;
    }

    @Override
    public @NotNull String getFamilyName() {
      return XmlPsiBundle.message("xml.quickfix.create.namespace.declaration.family");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      RncNameImpl myReference = (RncNameImpl)descriptor.getPsiElement();
      final String prefix = myReference.getPrefix();
      final PsiFileFactory factory = PsiFileFactory.getInstance(myReference.getProject());
      final RncFile psiFile = (RncFile)factory.createFileFromText("dummy.rnc",
                                                                  RncFileType.getInstance(),
                                                                   StringUtil.toLowerCase(myReference.getKind().name()) + " " + prefix + " = \"###\"");
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

      CodeStyleManager.getInstance(e.getManager().getProject()).reformatNewlyAddedElement(blockNode, newNode);

      final PsiElement literal = e.getLastChild();
      assert literal != null;

      final ASTNode literalNode = literal.getNode();
      assert literalNode != null;

      assert literalNode.getElementType() == RncTokenTypes.LITERAL;

      final int offset = literal.getTextRange().getStartOffset();

      literal.delete();

      VirtualFile virtualFile = myReference.getElement().getContainingFile().getVirtualFile();
      if (virtualFile != null) {
        Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile, offset), true);
        if (editor != null) {
          RncDecl rncDecl = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(e);

          final TemplateManager manager = TemplateManager.getInstance(project);
          final Template t = manager.createTemplate("", "");
          t.addTextSegment(" \"");
          Expression expression = new ConstantNode("");
          t.addVariable("uri", expression, expression, true);
          t.addTextSegment("\"");
          t.addEndVariable();

          editor.getCaretModel().moveToOffset(rncDecl.getTextRange().getEndOffset());
          manager.startTemplate(editor, t);
        }
      }
    }
  }
}
