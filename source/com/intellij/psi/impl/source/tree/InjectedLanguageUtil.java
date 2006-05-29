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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.SrcRepositoryPsiElement;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class InjectedLanguageUtil {
  private static final Key<CachedValue<List<Pair<PsiElement, TextRange>>>> INJECTED_PSI = Key.create("injectedPsi");

  @Nullable
  public static <T extends PsiLanguageInjectionHost> List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@NotNull final T host,
                                                                      @Nullable final LiteralTextEscaper<T> textEscaper) {
    CachedValue<List<Pair<PsiElement, TextRange>>> cachedPsi = host.getUserData(INJECTED_PSI);
    if (cachedPsi == null) {
      CachedValueProvider<List<Pair<PsiElement, TextRange>>> provider = new InjectedPsiProvider<T>(host, textEscaper);
      cachedPsi = host.getManager().getCachedValuesManager().createCachedValue(provider, false);
      host.putUserData(INJECTED_PSI, cachedPsi);
    }
    return cachedPsi.getValue();
  }

  private static <T extends PsiLanguageInjectionHost> PsiElement parseInjectedPsiFile(final T host,
                                                                                      final TextRange rangeInsideHost,
                                                 final PsiManager psiManager,
                                                 final Language language,
                                                 final VirtualFile virtualFile,
                                                 @Nullable LiteralTextEscaper<T> textEscaper) {
    final Project project = psiManager.getProject();
    final ParserDefinition parserDefinition = language.getParserDefinition();
    if (parserDefinition == null) return null;

    final PsiParser parser = parserDefinition.createParser(project);
    final IElementType root = parserDefinition.getFileNodeType();

    final String text = host.getText();
    StringBuilder outChars = new StringBuilder(text.length());
    if (textEscaper == null) {
      outChars.append(text, rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset());
    }
    else {
      boolean result = textEscaper.decode(host, rangeInsideHost, outChars);
      if (!result) return null;
    }
    final PsiBuilderImpl builder = new PsiBuilderImpl(language, project, null, outChars);
    final ASTNode parsedNode = parser.parse(root, builder);
    if (!(parsedNode instanceof FileElement)) return null;
    if (textEscaper != null) {
      patchLeafs(parsedNode, host, rangeInsideHost, textEscaper);
    }
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

  private static <T extends PsiLanguageInjectionHost>void patchLeafs(final ASTNode parsedNode, final T host, final TextRange rangeInsideHost, final LiteralTextEscaper<T> literalTextEscaper) {
    final String text = host.getText().substring(rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset());
    final Map<LeafElement, String> newTexts = new THashMap<LeafElement, String>();
    ((TreeElement)parsedNode).acceptTree(new RecursiveTreeElementVisitor(){
      protected boolean visitNode(TreeElement element) {
        return true;
      }

      public void visitLeaf(LeafElement leaf) {
        TextRange range = leaf.getTextRange();
        int offsetInSource = literalTextEscaper.getOffsetInSource(range.getStartOffset());
        int endOffsetInSource = literalTextEscaper.getOffsetInSource(range.getEndOffset());
        String sourceSubText = text.substring(offsetInSource, endOffsetInSource);
        String leafText = leaf.getText();
        if (!Comparing.strEqual(leafText, sourceSubText)) {
          newTexts.put(leaf, sourceSubText);
        }
      }
    });
    for (LeafElement leaf : newTexts.keySet()) {
      String newText = newTexts.get(leaf);
      leaf.setInternedText(newText);
    }
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

  @Nullable
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

  private static class InjectedPsiProvider<T extends PsiLanguageInjectionHost> implements CachedValueProvider<List<Pair<PsiElement, TextRange>>> {
    private final T myHost;
    private final LiteralTextEscaper<T> myTextEscaper;

    public InjectedPsiProvider(final T host, @Nullable LiteralTextEscaper<T> textEscaper) {
      myHost = host;
      myTextEscaper = textEscaper;
    }

    public Result<List<Pair<PsiElement, TextRange>>> compute() {
      final TextRange hostRange = myHost.getTextRange();
      PsiFile hostPsiFile = myHost.getContainingFile();
      final VirtualFile hostVirtualFile = hostPsiFile.getVirtualFile();
      final DocumentEx hostDocument = (DocumentEx)PsiDocumentManager.getInstance(myHost.getProject()).getDocument(hostPsiFile);
      final PsiManagerImpl psiManager = (PsiManagerImpl)myHost.getManager();
      final List<Pair<PsiElement, TextRange>> result = new SmartList<Pair<PsiElement, TextRange>>();
      InjectedLanguagePlaces placesRegistrar = new InjectedLanguagePlaces() {
        public void addPlace(@NotNull Language language, @NotNull TextRange rangeInsideHost) {
          final TextRange documentWindow = hostRange.cutOut(rangeInsideHost);
          DocumentRange documentRange = new DocumentRange(hostDocument, documentWindow);
          String newText = documentRange.getText();
          final VirtualFile virtualFile = new VirtualFileDelegate(hostVirtualFile, documentWindow, language, newText);
          FileDocumentManagerImpl.registerDocument(documentRange, virtualFile);
          PsiElement psi = parseInjectedPsiFile(myHost, rangeInsideHost, psiManager, language, virtualFile, myTextEscaper);
          if (psi != null) {
            psi.putUserData(ResolveUtil.INJECTED_IN_ELEMENT, myHost);//.getContainingFile());
          }
          result.add(new Pair<PsiElement,TextRange>(psi, rangeInsideHost));
        }
      };
      for (LanguageInjector injector : psiManager.getLanguageInjectors()) {
        injector.getLanguagesToInject(myHost, placesRegistrar);
      }
      return Result.createSingleDependency(result, myHost);
    }
  }

  public interface LiteralTextEscaper<T extends PsiLanguageInjectionHost> {
    boolean decode(T text, final TextRange rangeInsideHost, StringBuilder outChars);
    int getOffsetInSource(int offsetInDecoded);
  }
  public static class StringLiteralEscaper implements LiteralTextEscaper<PsiLiteralExpression> {
    public static final StringLiteralEscaper INSTANCE = new StringLiteralEscaper();
    private int[] outSourceOffsets;

    public boolean decode(PsiLiteralExpression host, final TextRange rangeInsideHost, StringBuilder outChars) {
      final String text = host.getText().substring(rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset());
      outSourceOffsets = new int[text.length() + 1];
      return PsiLiteralExpressionImpl.parseStringCharacters(text, outChars, outSourceOffsets);
    }

    public int getOffsetInSource(int offsetInDecoded) {
      return outSourceOffsets[offsetInDecoded];
    }
  }

  public static class XmlTextLiteralEscaper implements LiteralTextEscaper<XmlText> {
    public static final XmlTextLiteralEscaper INSTANCE = new XmlTextLiteralEscaper();
    private XmlText myXmlText;

    public boolean decode(XmlText host, final TextRange rangeInsideHost, StringBuilder outChars) {
      myXmlText = host;
      int startInDecoded = host.physicalToDisplay(rangeInsideHost.getStartOffset());
      int endInDecoded = host.physicalToDisplay(rangeInsideHost.getEndOffset());
      outChars.append(host.getValue(), startInDecoded, endInDecoded);
      return true;
    }

    public int getOffsetInSource(final int offsetInDecoded) {
      return myXmlText.displayToPhysical(offsetInDecoded);
    }
  }
  public static class XmlAttributeLiteralEscaper implements LiteralTextEscaper<XmlAttributeValue> {
    public static final XmlAttributeLiteralEscaper INSTANCE = new XmlAttributeLiteralEscaper();
    private XmlAttribute myXmlAttribute;

    public boolean decode(XmlAttributeValue host, final TextRange rangeInsideHost, StringBuilder outChars) {
      myXmlAttribute = (XmlAttribute)host.getParent();
      TextRange valueTextRange = myXmlAttribute.getValueTextRange();
      int startInDecoded = myXmlAttribute.physicalToDisplay(rangeInsideHost.getStartOffset() - valueTextRange.getStartOffset());
      int endInDecoded = myXmlAttribute.physicalToDisplay(rangeInsideHost.getEndOffset() - valueTextRange.getStartOffset());
      outChars.append(myXmlAttribute.getDisplayValue(), startInDecoded, endInDecoded);
      return true;
    }

    public int getOffsetInSource(final int offsetInDecoded) {
      return myXmlAttribute.displayToPhysical(offsetInDecoded);
    }
  }
}
