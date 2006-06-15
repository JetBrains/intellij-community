package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
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
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.SrcRepositoryPsiElement;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class InjectedLanguageUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtill");
  private static final Key<CachedValue<List<Pair<PsiElement, TextRange>>>> INJECTED_PSI = Key.create("injectedPsi");

  @Nullable
  public static <T extends PsiLanguageInjectionHost> List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@NotNull T host,
                                                                                                           @Nullable LiteralTextEscaper<T> textEscaper) {
    CachedValue<List<Pair<PsiElement, TextRange>>> cachedPsi = host.getUserData(INJECTED_PSI);
    if (cachedPsi == null) {
      CachedValueProvider<List<Pair<PsiElement, TextRange>>> provider = new InjectedPsiProvider<T>(host, textEscaper);
      cachedPsi = host.getManager().getCachedValuesManager().createCachedValue(provider, false);
      host.putUserData(INJECTED_PSI, cachedPsi);
    }
    return cachedPsi.getValue();
  }

  private static <T extends PsiLanguageInjectionHost> PsiElement parseInjectedPsiFile(@NotNull final T host,
                                                                                      @NotNull final TextRange rangeInsideHost,
                                                                                      @NotNull final Language language,
                                                                                      @NotNull final VirtualFileDelegate virtualFile,
                                                                                      @Nullable final LiteralTextEscaper<T> textEscaper) {
    final ParserDefinition parserDefinition = language.getParserDefinition();
    if (parserDefinition == null) return null;
    PsiManager psiManager = host.getManager();
    final Project project = psiManager.getProject();

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
    String parsedText = parsedNode.getText();
    String sourceRawText = host.getText().substring(rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset());
    LOG.assertTrue(parsedText.equals(sourceRawText));

    parsedNode.putUserData(TreeElement.MANAGER_KEY, psiManager);
    SingleRootFileViewProvider viewProvider = new SingleRootFileViewProvider(psiManager, virtualFile) {
      public FileViewProvider clone() {
        DocumentRange oldDocumentRange = ((VirtualFileDelegate)getVirtualFile()).getDocumentRange();
        RangeMarker documentWindow = oldDocumentRange.getTextRange();
        DocumentEx delegate = oldDocumentRange.getDelegate();
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        PsiFile hostFile = documentManager.getPsiFile(delegate);
        PsiFile hostPsiFileCopy = (PsiFile)hostFile.copy();

        TextRange oldTextRange = new TextRange(documentWindow.getStartOffset(), documentWindow.getEndOffset());
        T newHost = (T)findInjectionHost(hostPsiFileCopy.findElementAt(oldDocumentRange.getTextRange().getStartOffset()));
        List<Pair<PsiElement, TextRange>> newInjected = newHost.getInjectedPsi();
        if (newInjected == null) return null;
        for (Pair<PsiElement, TextRange> pair : newInjected) {
          PsiElement psi = pair.getFirst();
          TextRange rangeInsideHost = pair.getSecond();
          TextRange injectedRange = newHost.getTextRange().cutOut(rangeInsideHost);
          if (injectedRange.equals(oldTextRange)) {
            PsiFile newFile = (PsiFile)psi;
            return newFile.getViewProvider();
          }
        }

        return null;
      }
    };
    PsiFile psiFile = parserDefinition.createFile(viewProvider);
    SrcRepositoryPsiElement repositoryPsiElement = (SrcRepositoryPsiElement)psiFile;
    ((FileElement)parsedNode).setPsiElement(repositoryPsiElement);
    repositoryPsiElement.setTreeElement(parsedNode);
    viewProvider.forceCachedPsi(psiFile);
    if (host.isPhysical()) {
      SmartPsiElementPointer pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(host);
      psiFile.putUserData(ResolveUtil.INJECTED_IN_ELEMENT, pointer);
    }

    return parsedNode.getPsi();
  }

  private static <T extends PsiLanguageInjectionHost> void patchLeafs(final ASTNode parsedNode, final T host, final TextRange rangeInsideHost, final LiteralTextEscaper<T> literalTextEscaper) {
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
    ((TreeElement)parsedNode).acceptTree(new RecursiveTreeElementVisitor(){
      protected boolean visitNode(TreeElement element) {
        element.clearCaches();
        return true;
      }
    });
  }

  public static Editor getEditorForInjectedLanguage(final Editor editor, PsiFile file) {
    if (editor == null || file == null || editor instanceof EditorDelegate) return editor;

    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
    int offset = editor.getCaretModel().getOffset();
    return getEditorForInjectedLanguage(editor, file, offset);
  }

  public static Editor getEditorForInjectedLanguage(final Editor editor, final PsiFile file, final int offset) {
    PsiLanguageInjectionHost injectionHost = findInjectionHost(file.findElementAt(offset));
    if (injectionHost == null && offset != 0) {
      injectionHost = findInjectionHost(file.findElementAt(offset-1));
    }
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
  public static PsiLanguageInjectionHost findInjectionHost(PsiElement element) {
    if (element == null) return null;
    if (element instanceof PsiLanguageInjectionHost) {
      return (PsiLanguageInjectionHost)element;
    }
    element = element.getParent();
    if (element == null) return null;
    if (element instanceof PsiLanguageInjectionHost) {
      return (PsiLanguageInjectionHost)element;
    }
    element = element.getParent();
    if (element == null) return null;
    if (element instanceof PsiLanguageInjectionHost) {
      return (PsiLanguageInjectionHost)element;
    }
    return null;
  }

  private static class InjectedPsiProvider<T extends PsiLanguageInjectionHost> implements CachedValueProvider<List<Pair<PsiElement, TextRange>>> {
    private final SmartPsiElementPointer myHost;
    private final LiteralTextEscaper<T> myTextEscaper;

    public InjectedPsiProvider(final T host, @Nullable LiteralTextEscaper<T> textEscaper) {
      LOG.assertTrue(host.isValid());
      myHost = host.isPhysical() ? SmartPointerManager.getInstance(host.getProject()).createSmartPsiElementPointer(host) : new SmartPsiElementPointer() {
        public PsiElement getElement() {
          return host;
        }
      };
      myTextEscaper = textEscaper;
    }

    public Result<List<Pair<PsiElement, TextRange>>> compute() {
      final T host = (T)myHost.getElement();
      if (host == null) return null;
      final TextRange hostRange = host.getTextRange();
      PsiFile hostPsiFile = host.getContainingFile();
      VirtualFile virtualFile = hostPsiFile.getVirtualFile();
      if (virtualFile == null) {
        PsiFile originalFile = hostPsiFile.getOriginalFile();
        if (originalFile != null) virtualFile = originalFile.getVirtualFile();
      }
      if (virtualFile == null) return null;
      final VirtualFile hostVirtualFile = virtualFile;
      final DocumentEx hostDocument = (DocumentEx)hostPsiFile.getViewProvider().getDocument();
      final PsiManagerImpl psiManager = (PsiManagerImpl)host.getManager();
      final List<Pair<PsiElement, TextRange>> result = new SmartList<Pair<PsiElement, TextRange>>();
      InjectedLanguagePlaces placesRegistrar = new InjectedLanguagePlaces() {
        public void addPlace(@NotNull Language language, @NotNull TextRange rangeInsideHost) {
          TextRange documentWindow = hostRange.cutOut(rangeInsideHost);
          DocumentRange documentRange = new DocumentRange(hostDocument, documentWindow);
          String newText = documentRange.getText();
          VirtualFileDelegate virtualFile = new VirtualFileDelegate(hostVirtualFile, documentRange, language, newText);
          FileDocumentManagerImpl.registerDocument(documentRange, virtualFile);
          PsiElement psi = parseInjectedPsiFile(host, rangeInsideHost, language, virtualFile, myTextEscaper);
          psi = registerDocumentRange(documentRange, (PsiFile)psi);

          result.add(new Pair<PsiElement,TextRange>(psi, rangeInsideHost));
        }
      };
      for (LanguageInjector injector : psiManager.getLanguageInjectors()) {
        injector.getLanguagesToInject(host, placesRegistrar);
      }
      return new Result<List<Pair<PsiElement, TextRange>>>(result, host, host.getContainingFile());
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


  private static final Key<Map<RangeMarker,PsiFile>> INJECTED_DOCUMENTS_KEY = Key.create("INJECTED_DOCUMENTS_KEY");
  public static void commitAllInjectedDocuments(PsiDocumentManager documentManager, Document document) {
    Map<RangeMarker, PsiFile> injected = document.getUserData(INJECTED_DOCUMENTS_KEY);
    if (injected == null) return;
    PsiFile psiFile = documentManager.getPsiFile(document);
    ArrayList<RangeMarker> oldInjected = new ArrayList<RangeMarker>(injected.keySet());
    for (RangeMarker documentRange : oldInjected) {
      final PsiFileImpl oldFile = (PsiFileImpl)injected.get(documentRange);
      if (!documentRange.isValid() || oldFile == null) {
        injected.remove(documentRange);
      }
      PsiElement element = psiFile.findElementAt(documentRange.getStartOffset());
      PsiLanguageInjectionHost injectionHost = findInjectionHost(element);
      if (injectionHost == null) continue;
      // it is here reparse happens and old file contents replaced
      injectionHost.getInjectedPsi();
    }
  }
  private static PsiElement registerDocumentRange(final DocumentRange documentRange, final PsiFile injectedPsi) {
    DocumentEx document = documentRange.getDelegate();
    Map<RangeMarker, PsiFile> injected = document.getUserData(INJECTED_DOCUMENTS_KEY);

    if (injected == null) {
      injected = new THashMap<RangeMarker, PsiFile>(new TObjectHashingStrategy<RangeMarker>() {
        public int computeHashCode(final RangeMarker object) {
          return 0; //ranges can grow/shrink
        }

        public boolean equals(final RangeMarker o1, final RangeMarker o2) {
          return o1.getStartOffset() == o2.getStartOffset() && o1.getEndOffset() == o2.getEndOffset();
        }
      });
      document.putUserData(INJECTED_DOCUMENTS_KEY, injected);
    }

    RangeMarker marker = documentRange.getTextRange();
    final PsiFileImpl oldFile = (PsiFileImpl)injected.get(marker);
    if (oldFile == null) {
      injected.put(marker, injectedPsi);
      return injectedPsi;
    }

    // replace psi
    FileElement newFileElement = (FileElement)injectedPsi.getNode().copyElement();
    FileElement oldFileElement = oldFile.getTreeElement();

    if (!newFileElement.getText().equals(oldFileElement.getText())) {
      if (oldFileElement.getFirstChildNode() != null) {
        TreeUtil.removeRange(oldFileElement.getFirstChildNode(), null);
      }
      final ASTNode firstChildNode = newFileElement.getFirstChildNode();
      if (firstChildNode != null) {
        TreeUtil.addChildren(oldFileElement, (TreeElement)firstChildNode);
      }
      oldFileElement.setCharTable(newFileElement.getCharTable());
      oldFile.subtreeChanged();

      PsiDocumentManagerImpl.checkConsistency(oldFile, documentRange);
    }

    return oldFile;
  }
}
