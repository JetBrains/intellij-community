package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDialect;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.injected.DocumentWindow;
import com.intellij.openapi.editor.impl.injected.EditorWindow;
import com.intellij.openapi.editor.impl.injected.VirtualFileWindow;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.ParameterizedCachedValueImpl;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author cdr
 */
public class InjectedLanguageUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtill");
  private static final Key<ParameterizedCachedValue<Places>> INJECTED_PSI_KEY = Key.create("INJECTED_PSI");
  private static final Key<List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>>> HIGHLIGHT_TOKENS = Key.create("HIGHLIGHT_TOKENS");

  @Deprecated
  @Nullable
  public static <T extends PsiLanguageInjectionHost> List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@NotNull final T host) {
    final List<Pair<PsiElement, TextRange>> result = new SmartList<Pair<PsiElement, TextRange>>();
    enumerate(host, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<Pair<PsiLanguageInjectionHost, TextRange>> places) {
        for (Pair<PsiLanguageInjectionHost, TextRange> place : places) {
          if (place.getFirst() == host) {
            result.add(new Pair<PsiElement, TextRange>(injectedPsi, place.getSecond()));
          }
        }
      }
    }, true);
    return result.isEmpty() ? null : result;
  }

  private static Place parseInjectedPsiFile(@NotNull final Language language,
                                            @NotNull final VirtualFile hostVirtualFile,
                                            @NotNull final DocumentEx hostDocument,
                                            @NotNull String prefix,
                                            @NotNull String suffix,
                                            @NotNull List<Pair</*@NotNull*/PsiLanguageInjectionHost, /*@NotNull*/TextRange>> hosts) {
    final ParserDefinition parserDefinition = language.getParserDefinition();
    if (parserDefinition == null) return null;

    StringBuilder outChars = new StringBuilder();
    outChars.append(prefix);

    TextRange[] rangeInsideHosts = new TextRange[hosts.size()];
    TextRange[] rangesInsideHostDocument = new TextRange[hosts.size()];
    PsiLanguageInjectionHost[] injectionHosts = new PsiLanguageInjectionHost[hosts.size()];
    LiteralTextEscaper<PsiLanguageInjectionHost>[] escapers = new LiteralTextEscaper[hosts.size()];
    List<Pair<PsiLanguageInjectionHost, TextRange>> resultHosts = new ArrayList<Pair<PsiLanguageInjectionHost, TextRange>>(hosts.size());
    boolean isOneLineEditor = false;
    for (int i = 0; i < hosts.size(); i++) {
      Pair<PsiLanguageInjectionHost, TextRange> hostInfo = hosts.get(i);
      PsiLanguageInjectionHost host = injectionHosts[i] = hostInfo.getFirst();
      TextRange rangeInsideHost = hostInfo.getSecond();
      LiteralTextEscaper textEscaper = escapers[i] = host.createLiteralTextEscaper();
      isOneLineEditor |= textEscaper.isOneLine();
      TextRange relevantRange = textEscaper.getRelevantTextRange().intersection(rangeInsideHost);
      if (relevantRange == null) return null;
      boolean result = textEscaper.decode(relevantRange, outChars);
      if (!result) return null;
      rangeInsideHosts[i] = relevantRange;
      rangesInsideHostDocument[i] = relevantRange.shiftRight(host.getTextRange().getStartOffset());
      resultHosts.add(new Pair<PsiLanguageInjectionHost, TextRange>(host, relevantRange));
    }
    outChars.append(suffix);

    DocumentWindow documentWindow = new DocumentWindow(hostDocument, isOneLineEditor, prefix, suffix, rangesInsideHostDocument);
    PsiManagerEx psiManager = (PsiManagerEx)hosts.get(0).getFirst().getManager();
    final Project project = psiManager.getProject();
    VirtualFileWindow virtualFile = InjectedLanguageManagerImpl.getInstance().createVirtualFile(language, hostVirtualFile, documentWindow, outChars, project);

    DocumentImpl decodedDocument = new DocumentImpl(outChars);
    FileDocumentManagerImpl.registerDocument(decodedDocument, virtualFile);

    SingleRootFileViewProvider viewProvider = new MyFileViewProvider(project, virtualFile, injectionHosts);
    PsiFile psiFile = parserDefinition.createFile(viewProvider);
    assert psiFile.getViewProvider() instanceof MyFileViewProvider : psiFile.getViewProvider();

    SmartPsiElementPointer<PsiLanguageInjectionHost> pointer = createHostSmartPointer(injectionHosts[0]);
    psiFile.putUserData(ResolveUtil.INJECTED_IN_ELEMENT, pointer);
    psiFile.putUserData(PsiManagerImpl.LANGUAGE_DIALECT,language instanceof LanguageDialect ? (LanguageDialect)language:null);

    final ASTNode parsedNode = psiFile.getNode();
    if (!(parsedNode instanceof FileElement)) return null;

    List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> tokens = obtainHighlightTokensFromLexer(language, outChars, escapers, injectionHosts, rangeInsideHosts, virtualFile,
                                                                                documentWindow, project);

    assert outChars.toString().equals(parsedNode.getText()) : outChars + "\n---\n" + parsedNode.getText() + "\n---\n";
    String documentText = documentWindow.getText();
    patchLeafs(parsedNode, escapers, injectionHosts, rangeInsideHosts, prefix.length(), suffix.length());
    assert parsedNode.getText().equals(documentText) : documentText + "\n---\n" + parsedNode.getText() + "\n---\n";

    parsedNode.putUserData(TreeElement.MANAGER_KEY, psiManager);
    virtualFile.setContent(null, documentWindow.getText(), false);
    FileDocumentManagerImpl.registerDocument(documentWindow, virtualFile);
    synchronized (PsiLock.LOCK) {
      psiFile = registerDocument(documentWindow, psiFile);
      MyFileViewProvider myFileViewProvider = (MyFileViewProvider)psiFile.getViewProvider();
      myFileViewProvider.setVirtualFile(virtualFile);
      myFileViewProvider.forceCachedPsi(psiFile);
    }

    psiFile.putUserData(HIGHLIGHT_TOKENS, tokens);

    PsiDocumentManagerImpl.checkConsistency(psiFile, documentWindow);

    return new Place(psiFile, resultHosts);
  }

  public static List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> getHighlightTokens(PsiFile file) {
    return file.getUserData(HIGHLIGHT_TOKENS);
  }

  private static List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> obtainHighlightTokensFromLexer(Language language,
                                                                                                                 StringBuilder outChars,
                                                                                                                 LiteralTextEscaper<PsiLanguageInjectionHost>[] textEscapers,
                                                                                                                 PsiLanguageInjectionHost[] injectionHosts,
                                                                                                                 TextRange[] hostRanges,
                                                                                                                 VirtualFileWindow virtualFile,
                                                                                                                 DocumentWindow documentWindow,
                                                                                                                 Project project) {
    List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> tokens = new ArrayList<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>>(10);
    SyntaxHighlighter syntaxHighlighter = language.getSyntaxHighlighter(project, virtualFile);
    Lexer lexer = syntaxHighlighter.getHighlightingLexer();
    lexer.start(outChars, 0, outChars.length(), 0);
    for (IElementType tokenType; (tokenType = lexer.getTokenType()) != null; lexer.advance()) {
      TextRange textRange = new TextRange(lexer.getTokenStart(), lexer.getTokenEnd());
      TextRange editable = documentWindow.intersectWithEditable(textRange);
      if (editable == null || editable.getLength() == 0) continue;
      int i = documentWindow.getHostNumber(textRange.getStartOffset());
      if (i == -1) continue;
      int prevHostsCombinedLength = documentWindow.getPrevHostsCombinedLength(i);
      LiteralTextEscaper<PsiLanguageInjectionHost> textEscaper = textEscapers[i];

      TextRange rangeInHost;
      if (textEscaper == null) {
        rangeInHost = textRange.shiftRight(hostRanges[i].getStartOffset()-documentWindow.getPrefix().length() - prevHostsCombinedLength);
      }
      else {
        int startInHost = textEscaper.getOffsetInHost(editable.getStartOffset() - documentWindow.getPrefix().length() - prevHostsCombinedLength, hostRanges[i]);
        int endInHost = textEscaper.getOffsetInHost(editable.getEndOffset() - documentWindow.getPrefix().length() - prevHostsCombinedLength, hostRanges[i]);
        rangeInHost = new TextRange(startInHost, endInHost);
      }
      tokens.add(Trinity.create(tokenType, injectionHosts[i], rangeInHost));
    }
    return tokens;
  }

  private static class Place extends Pair<PsiFile, List<Pair<PsiLanguageInjectionHost, TextRange>>>{
    public Place(PsiFile first, List<Pair<PsiLanguageInjectionHost, TextRange>> second) {
      super(first, second);
    }
  }
  private static interface Places extends List<Place> {}
  private static class PlacesImpl extends SmartList<Place> implements Places {}

  public static void enumerate(PsiElement host, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor, boolean probeUp) {
    Places places = probeElementsUp(host, probeUp);
    if (places == null) return;
    for (Place place : places) {
      PsiFile injectedPsi = place.getFirst();
      List<Pair<PsiLanguageInjectionHost, TextRange>> pairs = place.getSecond();
      visitor.visit(injectedPsi, pairs);
    }
  }

  private static class MyFileViewProvider extends SingleRootFileViewProvider {
    private final PsiLanguageInjectionHost[] myHosts;

    private MyFileViewProvider(@NotNull Project project, @NotNull VirtualFileWindow virtualFile, PsiLanguageInjectionHost[] hosts) {
      super(PsiManager.getInstance(project), virtualFile);
      myHosts = hosts;
    }

    public void rootChanged(PsiFile psiFile) {
      super.rootChanged(psiFile);
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getManager().getProject());
      DocumentWindow documentWindow = (DocumentWindow)documentManager.getDocument(psiFile);

      String[] changes = documentWindow.calculateMinEditSequence(psiFile.getText());
      RangeMarker[] hostRanges = documentWindow.getHostRanges();
      assert changes.length == myHosts.length;
      for (int i = 0; i < myHosts.length; i++) {
        String change = changes[i];
        if (change != null) {
          PsiLanguageInjectionHost host = myHosts[i];
          RangeMarker hostRange = hostRanges[i];
          TextRange hostTextRange = host.getTextRange();
          TextRange range = hostTextRange.intersection(new TextRange(hostRange.getStartOffset(), hostRange.getEndOffset())).shiftRight(-hostTextRange.getStartOffset());
          String newHostText = StringUtil.replaceSubstring(host.getText(), range, change);
          host.fixText(newHostText);
        }
      }
    }

    public FileViewProvider clone() {
      final DocumentWindow oldDocumentRange = ((VirtualFileWindow)getVirtualFile()).getDocumentWindow();
      DocumentEx delegate = oldDocumentRange.getDelegate();
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getManager().getProject());
      PsiFile hostFile = documentManager.getPsiFile(delegate);
      PsiFile hostPsiFileCopy = (PsiFile)hostFile.copy();

      RangeMarker firstTextRange = oldDocumentRange.getFirstTextRange();
      PsiLanguageInjectionHost newHost = findInjectionHost(hostPsiFileCopy.findElementAt(firstTextRange.getStartOffset()));
      assert newHost != null;
      final Ref<FileViewProvider> provider = new Ref<FileViewProvider>();
      enumerate(newHost, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<Pair<PsiLanguageInjectionHost, TextRange>> places) {
          Document document = documentManager.getCachedDocument(injectedPsi);
          if (document instanceof DocumentWindow && oldDocumentRange.areRangesEqual((DocumentWindow)document)) {
            provider.set(injectedPsi.getViewProvider());
          }
        }
      }, true);
      return provider.get();
    }

    @Nullable
    protected PsiFile getPsiInner(Language target) {
      PsiFile file = super.getPsiInner(target);
      if (file == null || file.getContext() == null) return null;
      return file;
    }
  }

  private static <T extends PsiLanguageInjectionHost> void patchLeafs(final ASTNode parsedNode,
                                                                      final LiteralTextEscaper<T>[] escapers,
                                                                      final T[] injectionHosts,
                                                                      final TextRange[] rangeInsideHosts,
                                                                      final int prefixLength,
                                                                      final int suffixLength) {
    final TextRange contentsRange = new TextRange(prefixLength, parsedNode.getTextLength() - suffixLength);

    final Map<LeafElement, String> newTexts = new THashMap<LeafElement, String>();
    ((TreeElement)parsedNode).acceptTree(new RecursiveTreeElementVisitor(){
      int currentHostNum = 0;
      int currentInHostOffset = rangeInsideHosts[0].getStartOffset();
      LeafElement prevElement;
      String prevElementTail;
      int prevHostsCombinedLength = 0;

      protected boolean visitNode(TreeElement element) {
        return true;
      }

      public void visitLeaf(LeafElement leaf) {
        TextRange range = leaf.getTextRange();
        if (prefixLength > range.getStartOffset() && prefixLength < range.getEndOffset()) {
          LOG.error("Prefix must not contain text that will be glued with the element body after parsing. " +
                    "However, parsed element of "+leaf.getClass()+" contains "+(prefixLength-range.getStartOffset()) + " characters from the prefix. " +
                    "Parsed text is '"+leaf.getText()+"'");
        }
        if (range.getStartOffset() < contentsRange.getEndOffset() && contentsRange.getEndOffset() < range.getEndOffset()) {
          LOG.error("Suffix must not contain text that will be glued with the element body after parsing. " +
                    "However, parsed element of "+leaf.getClass()+" contains "+(range.getEndOffset()-contentsRange.getEndOffset()) + " characters from the suffix. " +
                    "Parsed text is '"+leaf.getText()+"'");
        }
        if (!contentsRange.contains(range)) return;
        int startOffsetInHost = currentInHostOffset;
        int endOffsetInHost;
        String leafEncodedText = "";
        while (true) {
          int end = range.getEndOffset() - prefixLength - prevHostsCombinedLength;
          endOffsetInHost = escapers[currentHostNum] == null ? end + rangeInsideHosts[currentHostNum].getStartOffset() : escapers[currentHostNum].getOffsetInHost(end, rangeInsideHosts[currentHostNum]);
          String hostText = injectionHosts[currentHostNum].getText();
          if (endOffsetInHost != -1) {
            leafEncodedText += hostText.substring(startOffsetInHost, endOffsetInHost);
            break;
          }
          String rest = hostText.substring(startOffsetInHost, rangeInsideHosts[currentHostNum].getEndOffset());
          leafEncodedText += rest;
          prevHostsCombinedLength += escapers[currentHostNum] == null ? rangeInsideHosts[currentHostNum].getLength() : escapers[currentHostNum].getOffsetInHost(rangeInsideHosts[currentHostNum].getLength(), rangeInsideHosts[currentHostNum]) - escapers[currentHostNum].getOffsetInHost(0, rangeInsideHosts[currentHostNum]);
          currentHostNum++;
          currentInHostOffset = startOffsetInHost = rangeInsideHosts[currentHostNum].getStartOffset();
        }
        if (leaf.getElementType() == TokenType.WHITE_SPACE && prevElementTail != null) {
          // optimization: put all garbage into whitespace
          leafEncodedText = prevElementTail + leafEncodedText;
          newTexts.remove(prevElement);
        }
        String leafText = leaf.getText();
        if (!Comparing.strEqual(leafText, leafEncodedText)) {
          newTexts.put(leaf, leafEncodedText);
        }
        if (leafEncodedText.startsWith(leafText) && leafEncodedText.length() != leafText.length()) {
          prevElementTail = leafEncodedText.substring(leafText.length());
        }
        else {
          prevElementTail = null;
        }
        currentInHostOffset += endOffsetInHost-startOffsetInHost;
        prevElement = leaf;
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
    if (editor == null || file == null || editor instanceof EditorWindow) return editor;

    int offset = editor.getCaretModel().getOffset();
    return getEditorForInjectedLanguage(editor, file, offset);
  }

  public static Editor getEditorForInjectedLanguage(final Editor editor, final PsiFile file, final int offset) {
    if (editor == null || file == null || editor instanceof EditorWindow) return editor;
    PsiFile injectedFile = findInjectedPsiAt(file, offset);
    if (injectedFile == null) return editor;
    Document document = PsiDocumentManager.getInstance(editor.getProject()).getDocument(injectedFile);
    DocumentWindow documentWindow = (DocumentWindow)document;
    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      int selstart = selectionModel.getSelectionStart();
      int selend = selectionModel.getSelectionEnd();
      if (!documentWindow.containsRange(selstart, selend)) {
        // selection spreads out the injected editor range
        return editor;
      }
    }
    return EditorWindow.create(documentWindow, (EditorImpl)editor, injectedFile);
  }

  public static PsiFile findInjectedPsiAt(PsiFile host, int offset) {
    PsiElement injected = findInjectedElementAt(host, offset);
    if (injected != null) {
      return injected.getContainingFile();
    }
    return null;
  }

  // consider injected elements
  public static PsiElement findElementAt(PsiFile file, int offset) {
    PsiElement injected = findInjectedElementAt(file, offset);
    if (injected != null) {
      return injected;
    }
    return file.findElementAt(offset);
  }

  private static Places probeElementsUp(PsiElement element, boolean probeUp) {
    for (PsiElement current = element; current != null; current = ResolveUtil.getContext(current)) {
      ParameterizedCachedValue<Places> data = current.getUserData(INJECTED_PSI_KEY);
      if (data != null) {
        Places value = data.getValue(current);
        if (value != null) {
          return value;
        }
      }
      InjectedPsiProvider provider = new InjectedPsiProvider();
      CachedValueProvider.Result<Places> result = provider.compute(current);
      if (result != null) {
        ParameterizedCachedValue<Places> cachedValue = current.getManager().getCachedValuesManager().createParameterizedCachedValue(provider, false);
        ((ParameterizedCachedValueImpl<Places>)cachedValue).setValue(result);
        Places places = result.getValue();
        for (Place place : places) {
          for (Pair<PsiLanguageInjectionHost, TextRange> pair : place.getSecond()) {
            PsiLanguageInjectionHost host = pair.getFirst();
            host.putUserData(INJECTED_PSI_KEY, cachedValue);
          }
        }
        current.putUserData(INJECTED_PSI_KEY, cachedValue);
        return places;
      }
      if (!probeUp) break;
    }
    return null;
  }

  private static PsiElement findInjectedElementAt(PsiFile file, final int offset) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
    documentManager.commitAllDocuments();

    PsiElement element = file.findElementAt(offset);
    PsiElement inj = findInside(element, offset, documentManager);
    if (inj != null) return inj;

    if (offset != 0) {
      PsiElement element1 = file.findElementAt(offset - 1);
      if (element1 != element) return findInside(element1, offset, documentManager);
    }

    return null;
  }

  private static PsiElement findInside(PsiElement element, final int offset, @NotNull final PsiDocumentManager documentManager) {
    final Ref<PsiElement> out = new Ref<PsiElement>();
    enumerate(element, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<Pair<PsiLanguageInjectionHost, TextRange>> places) {
        for (Pair<PsiLanguageInjectionHost, TextRange> place : places) {
          TextRange rangeInsideHost = place.getSecond();
          TextRange hostRange = place.getFirst().getTextRange();
          if (hostRange.cutOut(rangeInsideHost).grown(1).contains(offset)) {
            DocumentWindow document = (DocumentWindow)documentManager.getCachedDocument(injectedPsi);
            int injectedOffset = document.hostToInjected(offset);
            PsiElement injElement = injectedPsi.findElementAt(injectedOffset);
            out.set(injElement == null ? injectedPsi : injElement);
          }
        }
      }
    }, true);
    return out.get();
  }

  @Nullable
  public static PsiLanguageInjectionHost findInjectionHost(PsiElement element) {
    if (element == null || element instanceof PsiFile) return null;
    if ("EL".equals(element.getLanguage().getID())) return null;

    if (element instanceof PsiLanguageInjectionHost) {
      return (PsiLanguageInjectionHost)element;
    }
    element = element.getParent();
    if (element == null || element instanceof PsiFile) return null;
    if (element instanceof PsiLanguageInjectionHost) {
      return (PsiLanguageInjectionHost)element;
    }
    element = element.getParent();
    if (element == null || element instanceof PsiFile) return null;
    if (element instanceof PsiLanguageInjectionHost) {
      return (PsiLanguageInjectionHost)element;
    }
    return null;
  }

  private static class InjectedPsiProvider implements ParameterizedCachedValueProvider<Places> {
    public CachedValueProvider.Result<Places> compute(Object param) {
      final PsiElement element = (PsiElement)param;
      PsiFile hostPsiFile = element.getContainingFile();
      if (hostPsiFile == null) return null;
      FileViewProvider viewProvider = hostPsiFile.getViewProvider();
      VirtualFile virtualFile /*= hostPsiFile.getVirtualFile();
      if (virtualFile == null) {
        PsiFile originalFile = hostPsiFile.getOriginalFile();
        if (originalFile != null) virtualFile = originalFile.getVirtualFile();
      }
      if (virtualFile == null) virtualFile*/ = viewProvider.getVirtualFile();
      final VirtualFile hostVirtualFile = virtualFile;
      final DocumentEx hostDocument = (DocumentEx)viewProvider.getDocument();
      if (hostDocument == null) return null;

      final Places result = new PlacesImpl();
      InjectedLanguageManagerImpl injectedManager = InjectedLanguageManagerImpl.getInstance();
      if (injectedManager == null) return null; //for tests
      injectedManager.processInPlaceInjectorsFor(element, new Processor<InjectedLanguageManager.MultiPlaceInjector>() {
        public boolean process(InjectedLanguageManager.MultiPlaceInjector injector) {
          injector.getLanguagesToInject(element, new InjectedLanguageManager.MultiPlaceRegistrar() {
            public void addPlaces(@NotNull Language language, @NonNls @Nullable String prefix, @NonNls @Nullable String suffix,
                                  @NotNull List<Pair<PsiLanguageInjectionHost, TextRange>> hosts) {
              Place place = parseInjectedPsiFile(language, hostVirtualFile, hostDocument, prefix == null ? "" : prefix,
                                                    suffix == null ? "" : suffix, hosts);
              if (place != null) {
                result.add(place);
              }
            }
          });
          return result.isEmpty();
        }
      });

      if (result.isEmpty()) return null;
      return new CachedValueProvider.Result<Places>(result, PsiModificationTracker.MODIFICATION_COUNT, hostDocument);
    }

  }

  private static <T extends PsiLanguageInjectionHost> SmartPsiElementPointer<T> createHostSmartPointer(final T host) {
    return host.isPhysical()
           ? SmartPointerManager.getInstance(host.getProject()).createSmartPsiElementPointer(host)
           : new SmartPsiElementPointer<T>() {
             public T getElement() {
               return host;
             }
           };
  }

  private static final Key<List<DocumentWindow>> INJECTED_DOCS_KEY = Key.create("INJECTED_DOCS_KEY");
  @NotNull
  public static List<DocumentWindow> getCachedInjectedDocuments(@NotNull Document hostDocument) {
    List<DocumentWindow> injected = hostDocument.getUserData(INJECTED_DOCS_KEY);
    if (injected == null) {
      injected = new CopyOnWriteArrayList<DocumentWindow>();
      ((UserDataHolderEx)hostDocument).putUserDataIfAbsent(INJECTED_DOCS_KEY, injected);
    }
    return injected;
  }

  public static void commitAllInjectedDocuments(Document hostDocument, Project project) {
    List<DocumentWindow> injected = getCachedInjectedDocuments(hostDocument);
    if (injected.isEmpty()) return;

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    PsiFile hostPsiFile = documentManager.getPsiFile(hostDocument);
    for (DocumentWindow injDocument : injected) {
      final PsiFileImpl oldFile = (PsiFileImpl)documentManager.getCachedPsiFile(injDocument);
      if (!injDocument.isValid() || oldFile == null) {
        injected.remove(injDocument);
        continue;
      }
      RangeMarker rangeMarker = injDocument.getFirstTextRange();
      PsiElement element = hostPsiFile.findElementAt(rangeMarker.getStartOffset());
      PsiLanguageInjectionHost injectionHost = findInjectionHost(element);
      if (injectionHost == null) {
        injected.remove(injDocument);
        continue;
      }
      // it is here reparse happens and old file contents replaced
      enumerate(injectionHost, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<Pair<PsiLanguageInjectionHost, TextRange>> places) {
          PsiDocumentManagerImpl.checkConsistency(injectedPsi, injectedPsi.getViewProvider().getDocument());
        }
      }, true);
    }
    PsiDocumentManagerImpl.checkConsistency(hostPsiFile, hostDocument);
  }

  public static void clearCaches(PsiFile injected, DocumentWindow documentWindow) {
    VirtualFileWindow virtualFile = (VirtualFileWindow)injected.getVirtualFile();
    ((PsiManagerEx)injected.getManager()).getFileManager().setViewProvider(virtualFile, null);
    getCachedInjectedDocuments(documentWindow.getDelegate()).remove(documentWindow);
    InjectedLanguageManagerImpl.getInstance().clearCaches(virtualFile);
  }

  private static PsiFile registerDocument(final DocumentWindow documentWindow, final PsiFile injectedPsi) {
    DocumentEx hostDocument = documentWindow.getDelegate();
    List<DocumentWindow> injected = getCachedInjectedDocuments(hostDocument);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(injectedPsi.getProject());
    for (int i = injected.size()-1; i>=0; i--) {
      DocumentWindow oldDocument = injected.get(i);
      PsiFileImpl oldFile = (PsiFileImpl)documentManager.getCachedPsiFile(oldDocument);
      if (oldFile == null || !(oldFile.getViewProvider() instanceof MyFileViewProvider)) {
        injected.remove(i);
        continue;
      }       

      ASTNode injectedNode = injectedPsi.getNode();
      ASTNode oldFileNode = oldFile.getNode();
      assert injectedNode != null;
      assert oldFileNode != null;
      if (oldDocument.areRangesEqual(documentWindow)) {
        if (oldFile.getFileType() != injectedPsi.getFileType()) {
          injected.remove(i);
          continue;
        }
        oldFile.putUserData(ResolveUtil.INJECTED_IN_ELEMENT, injectedPsi.getUserData(ResolveUtil.INJECTED_IN_ELEMENT));
        if (!injectedNode.getText().equals(oldFileNode.getText())) {
          // replace psi
          FileElement newFileElement = (FileElement)injectedNode;//.copyElement();
          FileElement oldFileElement = oldFile.getTreeElement();

          if (oldFileElement.getFirstChildNode() != null) {
            TreeUtil.removeRange(oldFileElement.getFirstChildNode(), null);
          }
          final ASTNode firstChildNode = newFileElement.getFirstChildNode();
          if (firstChildNode != null) {
            TreeUtil.addChildren(oldFileElement, (TreeElement)firstChildNode);
          }
          oldFileElement.setCharTable(newFileElement.getCharTable());
          FileDocumentManagerImpl.registerDocument(documentWindow, oldFile.getVirtualFile());
          oldFile.subtreeChanged();

          SingleRootFileViewProvider viewProvider = (SingleRootFileViewProvider)oldFile.getViewProvider();
          viewProvider.setVirtualFile(injectedPsi.getVirtualFile());
        }
        return oldFile;
      }
    }
    injected.add(documentWindow);
    return injectedPsi;
  }

  public static Editor openEditorFor(PsiFile file, Project project) {
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    // may return editor injected in current selection in the host editor, not for the file passed as argument
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    if (virtualFile instanceof VirtualFileWindow) {
      virtualFile = ((VirtualFileWindow)virtualFile).getDelegate();
    }
    Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile, -1), false);
    if (editor == null || editor instanceof EditorWindow) return editor;
    if (document instanceof DocumentWindow) {
      return EditorWindow.create((DocumentWindow)document, (EditorImpl)editor, file);
    }
    return editor;
  }

  private static PsiFile getContainingInjectedFile(PsiElement element) {
    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) return null;
    PsiElement host = psiFile.getContext();
    return host == null ? null : psiFile;
  }
  public static boolean isInInjectedLanguagePrefixSuffix(final PsiElement element) {
    PsiFile injectedFile = getContainingInjectedFile(element);
    if (injectedFile == null) return false;
    Document document = PsiDocumentManager.getInstance(element.getProject()).getCachedDocument(injectedFile);
    if (!(document instanceof DocumentWindow)) return false;
    DocumentWindow documentWindow = (DocumentWindow)document;
    TextRange elementRange = element.getTextRange();
    TextRange editable = documentWindow.intersectWithEditable(elementRange);
    return !elementRange.equals(editable); //) throw new IncorrectOperationException("Can't change "+ UsageViewUtil.createNodeText(element, true));
  }
}
