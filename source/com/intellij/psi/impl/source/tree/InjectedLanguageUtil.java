package com.intellij.psi.impl.source.tree;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.SrcRepositoryPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.DocumentRange;
import com.intellij.openapi.editor.impl.VirtualFileDelegate;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lang.ASTNode;
import com.intellij.lang.impl.PsiBuilderImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class InjectedLanguageUtil {
  @Nullable
  public static Pair<PsiElement, TextRange> createInjectedPsiFile(@NotNull PsiLanguageInjectionHost host, @NotNull final String text, @NotNull final TextRange range) {
    final Language language = host.getManager().getInjectedLanguage(host);
    if (language == null) return null;

    final Project project = host.getProject();
    final ParserDefinition parserDefinition = language.getParserDefinition();
    if (parserDefinition == null) return null;

    final PsiParser parser = parserDefinition.createParser(project);
    final IElementType root = parserDefinition.getFileNodeType();

    final PsiBuilderImpl builder = new PsiBuilderImpl(language, project, null, text);
    final ASTNode parsedNode = parser.parse(root, builder);
    if (parsedNode instanceof FileElement) {
      parsedNode.putUserData(TreeElement.MANAGER_KEY, host.getManager());
      TextRange documentWindow = host.getTextRange().cutOut(range);
      DocumentEx document = (DocumentEx)PsiDocumentManager.getInstance(project).getDocument(host.getContainingFile());
      DocumentRange documentRange = new DocumentRange(document, documentWindow);
      final VirtualFile virtualFile = new VirtualFileDelegate(host.getContainingFile().getVirtualFile(), documentWindow, language, text);
      PsiFile psiFile = parserDefinition.createFile(new SingleRootFileViewProvider(host.getManager(), virtualFile));
      psiFile.putUserData(ResolveUtil.INJECTED_IN_ELEMENT, host);
      FileDocumentManagerImpl.registerDocument(documentRange, virtualFile);
      SrcRepositoryPsiElement repositoryPsiElement = (SrcRepositoryPsiElement)psiFile;
      ((FileElement)parsedNode).setPsiElement(repositoryPsiElement);
      repositoryPsiElement.setTreeElement(parsedNode);
    }

    return Pair.create(parsedNode.getPsi(), range);
  }
}
