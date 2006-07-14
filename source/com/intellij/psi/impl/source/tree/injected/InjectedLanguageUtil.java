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
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.injected.DocumentRange;
import com.intellij.openapi.editor.impl.injected.EditorDelegate;
import com.intellij.openapi.editor.impl.injected.VirtualFileDelegate;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.SrcRepositoryPsiElement;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.impl.source.xml.XmlAttributeValueImpl;
import com.intellij.psi.impl.source.xml.XmlTextImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
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
    if (!host.isPhysical()) {
      CachedValueProvider.Result<List<Pair<PsiElement, TextRange>>> result = new InjectedPsiProvider<T>(host, textEscaper).compute();
      return result == null ? null : result.getValue();
    }
    
    CachedValue<List<Pair<PsiElement, TextRange>>> cachedPsi = host.getUserData(INJECTED_PSI);
    if (cachedPsi == null) {
      InjectedPsiProvider<T> provider = new InjectedPsiProvider<T>(host, textEscaper);
      cachedPsi = host.getManager().getCachedValuesManager().createCachedValue(provider, false);
      host.putUserData(INJECTED_PSI, cachedPsi);
    }
    return cachedPsi.getValue();
  }

  private static <T extends PsiLanguageInjectionHost> PsiElement parseInjectedPsiFile(@NotNull final T host,
                                                                                      @NotNull final TextRange rangeInsideHost,
                                                                                      @NotNull final Language language,
                                                                                      @NotNull final VirtualFileDelegate virtualFile,
                                                                                      @NotNull DocumentRange documentRange,
                                                                                      @Nullable final LiteralTextEscaper<T> textEscaper,
                                                                                      @NotNull String prefix,
                                                                                      @NotNull String suffix) {
    final ParserDefinition parserDefinition = language.getParserDefinition();
    if (parserDefinition == null) return null;
    PsiFile hostFile = host.getContainingFile();
    if (hostFile == null) return null;
    PsiManager psiManager = host.getManager();
    final Project project = psiManager.getProject();

    final PsiParser parser = parserDefinition.createParser(project);
    final IElementType root = parserDefinition.getFileNodeType();

    final String text = host.getText();
    StringBuilder outChars = new StringBuilder(text.length());
    outChars.append(prefix);
    if (textEscaper == null) {
      outChars.append(text, rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset());
    }
    else {
      boolean result = textEscaper.decode(host, rangeInsideHost, outChars);
      if (!result) return null;
    }
    outChars.append(suffix);
    final PsiBuilderImpl builder = new PsiBuilderImpl(language, project, null, outChars);
    final ASTNode parsedNode = parser.parse(root, builder);
    if (!(parsedNode instanceof FileElement)) return null;
    if (textEscaper != null) {
      patchLeafs(parsedNode, host, rangeInsideHost, textEscaper, prefix, suffix);
    }
    String parsedText = parsedNode.getText();
    String sourceRawText = prefix + host.getText().substring(rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset()) + suffix;
    LOG.assertTrue(parsedText.equals(sourceRawText));

    parsedNode.putUserData(TreeElement.MANAGER_KEY, psiManager);
    SingleRootFileViewProvider viewProvider = createViewProvider(project, virtualFile);
    PsiFile psiFile = parserDefinition.createFile(viewProvider);
    SmartPsiElementPointer pointer = createHostSmartPointer(host);
    psiFile.putUserData(ResolveUtil.INJECTED_IN_ELEMENT, pointer);
    psiFile = (PsiFile)registerDocumentRange(documentRange, psiFile);
    SrcRepositoryPsiElement repositoryPsiElement = (SrcRepositoryPsiElement)psiFile;
    ((FileElement)parsedNode).setPsiElement(repositoryPsiElement);
    repositoryPsiElement.setTreeElement(parsedNode);
    ((SingleRootFileViewProvider)psiFile.getViewProvider()).forceCachedPsi(psiFile);
    return parsedNode.getPsi();
  }

  private static <T extends PsiLanguageInjectionHost> SingleRootFileViewProvider createViewProvider(final Project project, final VirtualFileDelegate virtualFile) {
    return new MyFileViewProvider<T>(project, virtualFile);
  }
  private static class MyFileViewProvider<T extends PsiLanguageInjectionHost> extends SingleRootFileViewProvider {
    public MyFileViewProvider(final Project project, final VirtualFileDelegate virtualFile) {
      super(PsiManager.getInstance(project), virtualFile);
    }

    public void rootChanged(PsiFile psiFile) {
      super.rootChanged(psiFile);
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getManager().getProject());
      DocumentRange documentRange = (DocumentRange)documentManager.getDocument(psiFile);
      String text = psiFile.getText();
      text = StringUtil.trimStart(text, documentRange.getPrefix());
      text = StringUtil.trimEnd(text, documentRange.getSuffix());
      PsiLanguageInjectionHost injectionHost = (PsiLanguageInjectionHost)psiFile.getContext();
      PsiFile hostFile = injectionHost.getContainingFile();
      TextRange hostTextRange = injectionHost.getTextRange();
      RangeMarker documentWindow = documentRange.getTextRange();
      TextRange rangeInsideHost = new TextRange(documentWindow.getStartOffset(), documentWindow.getEndOffset()).shiftRight(-hostTextRange.getStartOffset());
      String newHostText = StringUtil.replaceSubstring(injectionHost.getText(), rangeInsideHost, text);
      if (!newHostText.equals(injectionHost.getText())) {
        injectionHost.fixText(newHostText);

        PsiDocumentManagerImpl.checkConsistency(psiFile, documentRange);
        PsiDocumentManagerImpl.checkConsistency(hostFile, documentRange.getDelegate());
      }
    }

    public FileViewProvider clone() {
      DocumentRange oldDocumentRange = ((VirtualFileDelegate)getVirtualFile()).getDocumentRange();
      RangeMarker documentWindow = oldDocumentRange.getTextRange();
      DocumentEx delegate = oldDocumentRange.getDelegate();
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getManager().getProject());
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
  }

  private static <T extends PsiLanguageInjectionHost> void patchLeafs(final ASTNode parsedNode,
                                                                      final T host,
                                                                      final TextRange rangeInsideHost,
                                                                      final LiteralTextEscaper<T> literalTextEscaper, final String prefix,
                                                                      final String suffix) {
    final String text = prefix + host.getText().substring(rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset()) + suffix;
    final Map<LeafElement, String> newTexts = new THashMap<LeafElement, String>();
    ((TreeElement)parsedNode).acceptTree(new RecursiveTreeElementVisitor(){
      protected boolean visitNode(TreeElement element) {
        return true;
      }

      public void visitLeaf(LeafElement leaf) {
        TextRange range = leaf.getTextRange();
        int offsetInSource = literalTextEscaper.getOffsetInSource(range.getStartOffset()) + prefix.length();
        int endOffsetInSource = literalTextEscaper.getOffsetInSource(range.getEndOffset()) + prefix.length();
        String sourceSubText = text.substring(offsetInSource, endOffsetInSource);
        String leafText = leaf.getText();
        if (!Comparing.strEqual(leafText, sourceSubText)) {
          newTexts.put(leaf, sourceSubText);
        }
      }
    });
    for (LeafElement leaf : newTexts.keySet()) {
      String newText = newTexts.get(leaf);
      leaf.setText(newText);
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
    if (editor == null || file == null || editor instanceof EditorDelegate) return editor;
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
    private SmartPsiElementPointer myHostPointer;
    private final LiteralTextEscaper<T> myTextEscaper;
    private T myHost;

    public InjectedPsiProvider(final T host, @Nullable LiteralTextEscaper<T> textEscaper) {
      myHost = host;
      LOG.assertTrue(host.isValid());
      myTextEscaper = textEscaper;
    }

    private void fastenMyBelts(final T host) {
      if (myHostPointer == null) {
        myHostPointer = createHostSmartPointer(host);
        myHost = null;
      }
    }

    public Result<List<Pair<PsiElement, TextRange>>> compute() {
      final T host = myHostPointer == null ? myHost : (T)myHostPointer.getElement();
      if (host == null) return null;
      final List<Pair<PsiElement, TextRange>> result = queryInjectionHostForPsi(host);
      if (result == null) return null;
      fastenMyBelts(host); // create smart pointer only if necessary
      return new Result<List<Pair<PsiElement, TextRange>>>(result, host, PsiModificationTracker.MODIFICATION_COUNT);
    }

    private List<Pair<PsiElement, TextRange>> queryInjectionHostForPsi(final T host) {
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
        public void addPlace(@NotNull Language language, @NotNull TextRange rangeInsideHost, @Nullable String prefix, @Nullable String suffix) {
          TextRange documentWindow = hostRange.cutOut(rangeInsideHost);
          if (prefix == null) prefix="";
          if (suffix == null) suffix="";
          DocumentRange documentRange = new DocumentRange(hostDocument, documentWindow,prefix,suffix);
          String newText = documentRange.getText();
          VirtualFileDelegate virtualFile = new VirtualFileDelegate(hostVirtualFile, documentRange, language, newText);
          FileDocumentManagerImpl.registerDocument(documentRange, virtualFile);
          PsiElement psi = parseInjectedPsiFile(host, rangeInsideHost, language, virtualFile, documentRange, myTextEscaper, prefix, suffix);
          LOG.assertTrue(psi.getText().equals(documentRange.getText()));

          result.add(new Pair<PsiElement,TextRange>(psi, rangeInsideHost));
        }
      };
      for (LanguageInjector injector : psiManager.getLanguageInjectors()) {
        injector.getLanguagesToInject(host, placesRegistrar);
      }
      return result;
    }
  }

  private static SmartPsiElementPointer createHostSmartPointer(final PsiElement host) {
    return host.isPhysical() ? SmartPointerManager.getInstance(host.getProject()).createSmartPsiElementPointer(host) : new SmartPsiElementPointer() {
      public PsiElement getElement() {
        return host;
      }
    };
  }

  public interface LiteralTextEscaper<T extends PsiLanguageInjectionHost> {
    boolean decode(T text, final TextRange rangeInsideHost, StringBuilder outChars);
    int getOffsetInSource(int offsetInDecoded);
  }
  public static class StringLiteralEscaper implements LiteralTextEscaper<PsiLiteralExpressionImpl> {
    public static final StringLiteralEscaper INSTANCE = new StringLiteralEscaper();
    private int[] outSourceOffsets;

    public boolean decode(PsiLiteralExpressionImpl host, final TextRange rangeInsideHost, StringBuilder outChars) {
      final String text = host.getText().substring(rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset());
      outSourceOffsets = new int[text.length() + 1];
      return PsiLiteralExpressionImpl.parseStringCharacters(text, outChars, outSourceOffsets);
    }

    public int getOffsetInSource(int offsetInDecoded) {
      return outSourceOffsets[offsetInDecoded];
    }
  }

  public static class XmlTextLiteralEscaper implements LiteralTextEscaper<XmlTextImpl> {
    public static final XmlTextLiteralEscaper INSTANCE = new XmlTextLiteralEscaper();
    private XmlText myXmlText;

    public boolean decode(XmlTextImpl host, final TextRange rangeInsideHost, StringBuilder outChars) {
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
  public static class XmlAttributeLiteralEscaper implements LiteralTextEscaper<XmlAttributeValueImpl> {
    public static final XmlAttributeLiteralEscaper INSTANCE = new XmlAttributeLiteralEscaper();
    private XmlAttribute myXmlAttribute;

    public boolean decode(XmlAttributeValueImpl host, final TextRange rangeInsideHost, StringBuilder outChars) {
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


  private static final Key<List<DocumentRange>> INJECTED_FILES_KEY = Key.create("INJECTED_FILES_KEY");
  public static void commitAllInjectedDocuments(Document hostDocument, Project project) {
    List<DocumentRange> injected = hostDocument.getUserData(INJECTED_FILES_KEY);
    if (injected == null) return;
    List<DocumentRange> oldInjected = new ArrayList<DocumentRange>(injected);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    PsiFile psiFile = documentManager.getPsiFile(hostDocument);
    for (DocumentRange injDocument : oldInjected) {
      final PsiFileImpl oldFile = (PsiFileImpl)documentManager.getPsiFile(injDocument);
      RangeMarker rangeMarker = injDocument.getTextRange();
      if (!rangeMarker.isValid() || oldFile == null) {
        injected.remove(injDocument);
      }
      PsiElement element = psiFile.findElementAt(rangeMarker.getStartOffset());
      PsiLanguageInjectionHost injectionHost = findInjectionHost(element);
      if (injectionHost == null) continue;
      // it is here reparse happens and old file contents replaced
      injectionHost.getInjectedPsi();
    }
  }
  private static PsiElement registerDocumentRange(final DocumentRange documentRange, final PsiFile injectedPsi) {
    DocumentEx hostDocument = documentRange.getDelegate();
    List<DocumentRange> injected = hostDocument.getUserData(INJECTED_FILES_KEY);

    if (injected == null) {
      injected = new SmartList<DocumentRange>();
      hostDocument.putUserData(INJECTED_FILES_KEY, injected);
    }

    RangeMarker rangeMarker = documentRange.getTextRange();
    TextRange textRange = new TextRange(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(injectedPsi.getProject());
    for (DocumentRange oldDocument : injected) {
      PsiFileImpl oldFile = (PsiFileImpl)documentManager.getPsiFile(oldDocument);
      RangeMarker oldRangeMarker = oldDocument.getTextRange();
      TextRange oldTextRange = new TextRange(oldRangeMarker.getStartOffset(), oldRangeMarker.getEndOffset());
      if (oldTextRange.intersects(textRange) &&
          !injectedPsi.getNode().getText().equals(oldFile.getNode().getText())) {
        // replace psi
        FileElement newFileElement = (FileElement)injectedPsi.getNode().copyElement();
        FileElement oldFileElement = oldFile.getTreeElement();

        if (oldFileElement.getFirstChildNode() != null) {
          TreeUtil.removeRange(oldFileElement.getFirstChildNode(), null);
        }
        final ASTNode firstChildNode = newFileElement.getFirstChildNode();
        if (firstChildNode != null) {
          TreeUtil.addChildren(oldFileElement, (TreeElement)firstChildNode);
        }
        oldFileElement.setCharTable(newFileElement.getCharTable());
        oldFile.putUserData(ResolveUtil.INJECTED_IN_ELEMENT, injectedPsi.getUserData(ResolveUtil.INJECTED_IN_ELEMENT));
        FileDocumentManagerImpl.registerDocument(documentRange, oldFile.getVirtualFile());
        oldFile.subtreeChanged();

        PsiDocumentManagerImpl.checkConsistency(oldFile, documentRange);
        SingleRootFileViewProvider viewProvider = (SingleRootFileViewProvider)oldFile.getViewProvider();
        viewProvider.setVirtualFile(injectedPsi.getVirtualFile());
        return oldFile;
      }
    }
    injected.add(documentRange);
    return injectedPsi;
  }

}
