package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.DocumentRange;
import com.intellij.openapi.editor.impl.EditorDelegate;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.VirtualFileDelegate;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SrcRepositoryPsiElement;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author cdr
 */
public class InjectedLanguageUtil {
  private static final Key<CachedValue<List<Pair<PsiElement, TextRange>>>> INJECTED_PSI = Key.create("injectedPsi");

  @Nullable
  public static List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@NotNull final PsiLanguageInjectionHost host) {
    CachedValue<List<Pair<PsiElement, TextRange>>> cachedPsi = host.getUserData(INJECTED_PSI);
    if (cachedPsi == null) {
      CachedValueProvider<List<Pair<PsiElement, TextRange>>> provider = new CachedValueProvider<List<Pair<PsiElement, TextRange>>>() {
        public Result<List<Pair<PsiElement, TextRange>>> compute() {
          TextRange hostRange = host.getTextRange();
          final VirtualFile hostVirtualFile = host.getContainingFile().getVirtualFile();
          final DocumentEx document = (DocumentEx)PsiDocumentManager.getInstance(host.getProject()).getDocument(host.getContainingFile());
          List<Pair<Language,TextRange>> injectedLanguages = host.getManager().getInjectedLanguages(host);
          List<Pair<PsiElement, TextRange>> result = null;
          if (injectedLanguages != null) {
            result = new SmartList<Pair<PsiElement, TextRange>>();
            for (Pair<Language, TextRange> pair : injectedLanguages) {
              TextRange range = pair.getSecond();
              final TextRange documentWindow = hostRange.cutOut(range);
              DocumentRange documentRange = new DocumentRange(document, documentWindow);
              Language language = pair.getFirst();
              String newText = documentRange.getText();
              final VirtualFile virtualFile = new VirtualFileDelegate(hostVirtualFile, documentWindow, language, newText);
              FileDocumentManagerImpl.registerDocument(documentRange, virtualFile);
              PsiElement psi = parseInjectedPsiFile(newText, host.getManager(), language, virtualFile);
              if (psi != null) {
                psi.putUserData(ResolveUtil.INJECTED_IN_ELEMENT, host);//.getContainingFile());
              }
              result.add(new Pair<PsiElement,TextRange>(psi, range));
            }
          }
          return Result.createSingleDependency(result, host);
        }
      };
      cachedPsi = host.getManager().getCachedValuesManager().createCachedValue(provider, false);
      host.putUserData(INJECTED_PSI, cachedPsi);
    }
    return cachedPsi.getValue();
  }

  private static PsiElement parseInjectedPsiFile(final String text, final PsiManager psiManager,
                                                                  final Language language, final VirtualFile virtualFile) {
    if (language == null) return null;

    final Project project = psiManager.getProject();
    final ParserDefinition parserDefinition = language.getParserDefinition();
    if (parserDefinition == null) return null;

    final PsiParser parser = parserDefinition.createParser(project);
    final IElementType root = parserDefinition.getFileNodeType();

    final PsiBuilderImpl builder = new PsiBuilderImpl(language, project, null, text);
    final ASTNode parsedNode = parser.parse(root, builder);
    if (!(parsedNode instanceof FileElement)) return null;
    parsedNode.putUserData(TreeElement.MANAGER_KEY, psiManager);
    SingleRootFileViewProvider viewProvider = new SingleRootFileViewProvider(psiManager, virtualFile) {
      public FileViewProvider clone() {
        FileViewProvider providerClone = super.clone();
        FileDocumentManagerImpl.registerDocument(providerClone.getDocument(), providerClone.getVirtualFile());
        return providerClone;
      }
    };
    PsiFile psiFile = parserDefinition.createFile(viewProvider);
    SrcRepositoryPsiElement repositoryPsiElement = (SrcRepositoryPsiElement)psiFile;
    ((FileElement)parsedNode).setPsiElement(repositoryPsiElement);
    repositoryPsiElement.setTreeElement(parsedNode);
    viewProvider.forceCachedPsi(psiFile);
    return parsedNode.getPsi();
  }

  public static Editor getEditorForInjectedLanguage(final Editor editor, PsiFile file) {
    if (editor == null) return null;
    if (file == null) return editor;

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    PsiLanguageInjectionHost injectionHost = findInjectionHost(element);
    List<Pair<PsiElement, TextRange>> injectedPsi = injectionHost == null ? null : injectionHost.getInjectedPsi();
    if (injectedPsi == null) {
      return editor;
    }
    TextRange hostRange = injectionHost.getTextRange();
    for (Pair<PsiElement, TextRange> pair : injectedPsi) {
      TextRange range = pair.getSecond();
      if (hostRange.cutOut(range).grown(1).contains(offset)) {
        PsiFile injectedFile = pair.getFirst().getContainingFile();
        Document document = PsiDocumentManager.getInstance(editor.getProject()).getDocument(injectedFile);

        return new EditorDelegate((DocumentRange)document, (EditorImpl)editor, injectedFile);
      }
    }
    return editor;
  }

  private static PsiLanguageInjectionHost findInjectionHost(final PsiElement element) {
    if (element instanceof PsiLanguageInjectionHost) {
      return (PsiLanguageInjectionHost)element;
    }
    else if (element != null && element.getParent()instanceof PsiLanguageInjectionHost) {
      return (PsiLanguageInjectionHost)element.getParent();
    }
    else {
      return null;
    }
  }
}
