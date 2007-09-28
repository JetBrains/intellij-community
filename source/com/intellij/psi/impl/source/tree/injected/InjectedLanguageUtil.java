package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDialect;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lexer.Lexer;
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
  private static final Key<ParameterizedCachedValue<Places, PsiElement>> INJECTED_PSI_KEY = Key.create("INJECTED_PSI");
  private static final Key<List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>>> HIGHLIGHT_TOKENS = Key.create("HIGHLIGHT_TOKENS");

  @Deprecated
  @Nullable
  public static <T extends PsiLanguageInjectionHost> List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@NotNull final T host) {
    final List<Pair<PsiElement, TextRange>> result = new SmartList<Pair<PsiElement, TextRange>>();
    enumerate(host, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        for (PsiLanguageInjectionHost.Shred place : places) {
          if (place.host == host) {
            result.add(new Pair<PsiElement, TextRange>(injectedPsi, place.getRangeInsideHost()));
          }
        }
      }
    });
    return result.isEmpty() ? null : result;
  }

  public static TextRange toTextRange(RangeMarker marker) {
    return new TextRange(marker.getStartOffset(), marker.getEndOffset());
  }


  public static List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> getHighlightTokens(PsiFile file) {
    return file.getUserData(HIGHLIGHT_TOKENS);
  }

  private static List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> obtainHighlightTokensFromLexer(Language language,
                                                                                                                 StringBuilder outChars,
                                                                                                                 List<LiteralTextEscaper<PsiLanguageInjectionHost>> escapers,
                                                                                                                 List<PsiLanguageInjectionHost.Shred> shreds,
                                                                                                                 VirtualFileWindow virtualFile,
                                                                                                                 DocumentWindow documentWindow,
                                                                                                                 Project project) {
    List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> tokens = new ArrayList<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>>(10);
    SyntaxHighlighter syntaxHighlighter = language.getSyntaxHighlighter(project, virtualFile);
    Lexer lexer = syntaxHighlighter.getHighlightingLexer();
    lexer.start(outChars, 0, outChars.length(), 0);
    int hostNum = 0;
    int prevHostsCombinedLength = 0;
    nextToken:
    for (IElementType tokenType = lexer.getTokenType(); tokenType != null; lexer.advance(), tokenType = lexer.getTokenType()) {
      TextRange range = new TextRange(lexer.getTokenStart(), lexer.getTokenEnd());
      while (!range.isEmpty()) {
        if (range.getStartOffset() >= shreds.get(hostNum).range.getEndOffset()) {
          hostNum++;
          prevHostsCombinedLength = range.getStartOffset();
        }
        TextRange editable = documentWindow.intersectWithEditable(range);
        if (editable == null || editable.getLength() == 0) continue nextToken;
        editable = editable.intersection(shreds.get(hostNum).range);
        if (editable == null || editable.getLength() == 0) continue nextToken;
        range = new TextRange(editable.getEndOffset(), range.getEndOffset());
        PsiLanguageInjectionHost host = shreds.get(hostNum).host;
        LiteralTextEscaper<PsiLanguageInjectionHost> escaper = escapers.get(hostNum);
        TextRange rangeInsideHost = shreds.get(hostNum).getRangeInsideHost();
        int prefixLength = shreds.get(hostNum).prefix.length();
        int start = escaper.getOffsetInHost(editable.getStartOffset() - prevHostsCombinedLength - prefixLength, rangeInsideHost);
        int end = escaper.getOffsetInHost(editable.getEndOffset() - prevHostsCombinedLength - prefixLength, rangeInsideHost);
        if (end == -1) {
          end = rangeInsideHost.getEndOffset();
          tokens.add(Trinity.create(tokenType, host, new TextRange(start, end)));
          prevHostsCombinedLength = shreds.get(hostNum).range.getEndOffset();
          range = new TextRange(shreds.get(hostNum).range.getEndOffset(), range.getEndOffset());
        }
        else {
          TextRange rangeInHost = new TextRange(start, end);
          tokens.add(Trinity.create(tokenType, host, rangeInHost));
        }
      }
    }
    return tokens;
  }

  private static class Place {
    private final PsiFile myInjectedPsi;
    private final List<PsiLanguageInjectionHost.Shred> myShreds;

    public Place(PsiFile injectedPsi, List<PsiLanguageInjectionHost.Shred> shreds) {
      myShreds = shreds;
      myInjectedPsi = injectedPsi;
    }
  }
  private static interface Places extends List<Place> {}
  private static class PlacesImpl extends SmartList<Place> implements Places {}

  public static void enumerate(@NotNull PsiElement host, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {
    PsiFile containingFile = host.getContainingFile();
    enumerate(host, containingFile, visitor, true);
  }
  public static void enumerate(@NotNull PsiElement host, @NotNull PsiFile containingFile, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor, boolean probeUp) {
    Places places = probeElementsUp(host, containingFile, probeUp);
    if (places == null) return;
    for (Place place : places) {
      PsiFile injectedPsi = place.myInjectedPsi;
      List<PsiLanguageInjectionHost.Shred> pairs = place.myShreds;

      visitor.visit(injectedPsi, pairs);
    }
  }

  private static class MyFileViewProvider extends SingleRootFileViewProvider {
    private PsiLanguageInjectionHost[] myHosts;
    private Project myProject;

    private MyFileViewProvider(@NotNull Project project, @NotNull VirtualFileWindow virtualFile, List<PsiLanguageInjectionHost> hosts) {
      super(PsiManager.getInstance(project), virtualFile);
      myHosts = hosts.toArray(new PsiLanguageInjectionHost[hosts.size()]);
      myProject = myHosts[0].getProject();
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
          TextRange rangeInsideHost = hostTextRange.intersection(toTextRange(hostRange)).shiftRight(-hostTextRange.getStartOffset());
          String newHostText = StringUtil.replaceSubstring(host.getText(), rangeInsideHost, change);
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
      PsiElement elementCopy = hostPsiFileCopy.findElementAt(firstTextRange.getStartOffset());
      assert elementCopy != null;
      final Ref<FileViewProvider> provider = new Ref<FileViewProvider>();
      enumerate(elementCopy, hostPsiFileCopy, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
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

    private void replace(PsiFile injectedPsi, List<PsiLanguageInjectionHost.Shred> shreds) {
      setVirtualFile(injectedPsi.getVirtualFile());
      myHosts = new PsiLanguageInjectionHost[shreds.size()];
      for (int i = 0; i < shreds.size(); i++) {
        PsiLanguageInjectionHost.Shred shred = shreds.get(i);
        myHosts[i] = shred.host;
      }
      myProject = myHosts[0].getProject();
    }

    private boolean isValid() {
      return !myProject.isDisposed();
    }
  }

  private static void patchLeafs(final ASTNode parsedNode,
                                 final List<LiteralTextEscaper<PsiLanguageInjectionHost>> escapers,
                                 final List<PsiLanguageInjectionHost.Shred> shreds) {
    final Map<LeafElement, String> newTexts = new THashMap<LeafElement, String>();
    ((TreeElement)parsedNode).acceptTree(new RecursiveTreeElementVisitor(){
      int currentHostNum = 0;
      LeafElement prevElement;
      String prevElementTail;
      int prevHostsCombinedLength = 0;
      TextRange shredHostRange = TextRange.from(shreds.get(0).prefix.length(), shreds.get(0).getRangeInsideHost().getLength());
      TextRange rangeInsideHost = shreds.get(currentHostNum).getRangeInsideHost();
      String hostText = shreds.get(currentHostNum).host.getText();
      PsiLanguageInjectionHost.Shred shred = shreds.get(currentHostNum);

      protected boolean visitNode(TreeElement element) {
        return true;
      }

      public void visitLeaf(LeafElement leaf) {
        TextRange range = leaf.getTextRange();
        int prefixLength = shredHostRange.getStartOffset();
        if (prefixLength > range.getStartOffset() && prefixLength < range.getEndOffset()) {
          //LOG.error("Prefix must not contain text that will be glued with the element body after parsing. " +
          //          "However, parsed element of "+leaf.getClass()+" contains "+(prefixLength-range.getStartOffset()) + " characters from the prefix. " +
          //          "Parsed text is '"+leaf.getText()+"'");
        }
        if (range.getStartOffset() < shredHostRange.getEndOffset() && shredHostRange.getEndOffset() < range.getEndOffset()) {
          //LOG.error("Suffix must not contain text that will be glued with the element body after parsing. " +
          //          "However, parsed element of "+leaf.getClass()+" contains "+(range.getEndOffset()-shredHostRange.getEndOffset()) + " characters from the suffix. " +
          //          "Parsed text is '"+leaf.getText()+"'");
        }

        int start = range.getStartOffset() - prevHostsCombinedLength;
        if (start < prefixLength) return;
        int end = range.getEndOffset() - prevHostsCombinedLength;
        if (end > shred.range.getEndOffset() - shred.suffix.length() && end <= shred.range.getEndOffset()) return;
        int startOffsetInHost = escapers.get(currentHostNum).getOffsetInHost(start - prefixLength, rangeInsideHost);
        
        if (startOffsetInHost == -1 || startOffsetInHost == rangeInsideHost.getEndOffset()) {
          // no way next leaf might stand more than one shred apart
          incHostNum(range.getStartOffset());
          start = range.getStartOffset() - prevHostsCombinedLength;
          startOffsetInHost = escapers.get(currentHostNum).getOffsetInHost(start - prefixLength, rangeInsideHost);
          assert startOffsetInHost != -1;
        }

        String leafEncodedText = "";
        while (true) {
          if (range.getEndOffset() <= shred.range.getEndOffset()) {
            end = range.getEndOffset() - prevHostsCombinedLength;
            int endOffsetInHost = escapers.get(currentHostNum).getOffsetInHost(end - prefixLength, rangeInsideHost);
            assert endOffsetInHost != -1;
            leafEncodedText += hostText.substring(startOffsetInHost, endOffsetInHost);
            break;
          }
          String rest = hostText.substring(startOffsetInHost, rangeInsideHost.getEndOffset());
          leafEncodedText += rest;
          incHostNum(shred.range.getEndOffset());
          startOffsetInHost = shred.getRangeInsideHost().getStartOffset();
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
        prevElement = leaf;
      }

      private void incHostNum(int startOffset) {
        currentHostNum++;
        prevHostsCombinedLength = startOffset;
        shred = shreds.get(currentHostNum);
        shredHostRange = TextRange.from(shred.prefix.length(), shred.getRangeInsideHost().getLength());
        rangeInsideHost = shred.getRangeInsideHost();
        hostText = shred.host.getText();
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

  private static final InjectedPsiProvider INJECTED_PSI_PROVIDER = new InjectedPsiProvider();
  private static Places probeElementsUp(final PsiElement element, PsiFile hostPsiFile, boolean probeUp) {
    if (hostPsiFile == null) return null;

    PsiManager psiManager = hostPsiFile.getManager();
    final Project project = psiManager.getProject();
    InjectedLanguageManagerImpl injectedManager = InjectedLanguageManagerImpl.getInstanceImpl(project);
    if (injectedManager == null) return null; //for tests

    for (PsiElement current = element; current != null && current != hostPsiFile; current = current.getParent()) {
      if ("EL".equals(current.getLanguage().getID())) break;
      ParameterizedCachedValue<Places,PsiElement> data = current.getUserData(INJECTED_PSI_KEY);
      if (data != null) {
        Places value = data.getValue(current);
        if (value != null) {
          return value;
        }
      }
      Places places = InjectedPsiProvider.doCompute(current, injectedManager, project, hostPsiFile);
      if (places != null) {
        ParameterizedCachedValue<Places,PsiElement> cachedValue = psiManager.getCachedValuesManager().createParameterizedCachedValue(INJECTED_PSI_PROVIDER, false);
        Document hostDocument = hostPsiFile.getViewProvider().getDocument();
        CachedValueProvider.Result<Places> result = new CachedValueProvider.Result<Places>(places, PsiModificationTracker.MODIFICATION_COUNT, hostDocument);
        ((ParameterizedCachedValueImpl<Places,PsiElement>)cachedValue).setValue(result);
        for (Place place : places) {
          for (PsiLanguageInjectionHost.Shred pair : place.myShreds) {
            pair.host.putUserData(INJECTED_PSI_KEY, cachedValue);
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

    PsiElement element = file.getViewProvider().findElementAt(offset, file.getLanguage());
    PsiElement inj = element == null ? null : findInside(element, file, offset, documentManager);
    if (inj != null) return inj;

    if (offset != 0) {
      PsiElement element1 = file.findElementAt(offset - 1);
      if (element1 != element && element1 != null) return findInside(element1, file, offset, documentManager);
    }

    return null;
  }

  private static PsiElement findInside(@NotNull PsiElement element, PsiFile file, final int offset, @NotNull final PsiDocumentManager documentManager) {
    final Ref<PsiElement> out = new Ref<PsiElement>();
    enumerate(element, file, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        for (PsiLanguageInjectionHost.Shred place : places) {
          TextRange hostRange = place.host.getTextRange();
          if (hostRange.cutOut(place.getRangeInsideHost()).grown(1).contains(offset)) {
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

  private static class InjectedPsiProvider implements ParameterizedCachedValueProvider<Places, PsiElement> {
    public CachedValueProvider.Result<Places> compute(PsiElement element) {
      PsiFile hostPsiFile = element.getContainingFile();
      if (hostPsiFile == null) return null;
      FileViewProvider viewProvider = hostPsiFile.getViewProvider();
      final DocumentEx hostDocument = (DocumentEx)viewProvider.getDocument();
      if (hostDocument == null) return null;

      PsiManager psiManager = viewProvider.getManager();
      final Project project = psiManager.getProject();
      InjectedLanguageManagerImpl injectedManager = InjectedLanguageManagerImpl.getInstanceImpl(project);
      if (injectedManager == null) return null; //for tests
      final Places result = doCompute(element, injectedManager, project, hostPsiFile);

      if (result == null) return null;
      return new CachedValueProvider.Result<Places>(result, PsiModificationTracker.MODIFICATION_COUNT, hostDocument);
    }

    @Nullable
    private static Places doCompute(final PsiElement element, InjectedLanguageManagerImpl injectedManager, Project project,
                                    PsiFile hostPsiFile) {
      MyInjProcessor processor = new MyInjProcessor(injectedManager, project, hostPsiFile);
      injectedManager.processInPlaceInjectorsFor(element, processor);
      return processor.hostRegistrar == null ? null : processor.hostRegistrar.result;
    }
    private static class MyInjProcessor implements InjectedLanguageManagerImpl.InjProcessor {
      private MyMultiHostRegistrar hostRegistrar;
      private final InjectedLanguageManagerImpl myInjectedManager;
      private final Project myProject;
      private final PsiFile myHostPsiFile;

      public MyInjProcessor(InjectedLanguageManagerImpl injectedManager, Project project, PsiFile hostPsiFile) {
        myInjectedManager = injectedManager;
        myProject = project;
        myHostPsiFile = hostPsiFile;
      }

      public boolean process(PsiElement element, MultiHostInjector injector) {
        if (hostRegistrar == null) {
          hostRegistrar = new MyMultiHostRegistrar(myProject, myInjectedManager, myHostPsiFile);
        }
        injector.getLanguagesToInject(hostRegistrar, element);
        return hostRegistrar.result == null;
      }
    }

    private static class MyMultiHostRegistrar implements MultiHostRegistrar {
      private Places result;
      private Language myLanguage;
      private List<TextRange> relevantRangesInHostDocument;
      private List<String> prefixes;
      private List<String> suffixes;
      private List<PsiLanguageInjectionHost> injectionHosts;
      private List<LiteralTextEscaper<PsiLanguageInjectionHost>> escapers;
      private List<PsiLanguageInjectionHost.Shred> shreds;
      private StringBuilder outChars;
      boolean isOneLineEditor;
      boolean cleared;
      private final Project myProject;
      private PsiManager myPsiManager;
      private DocumentEx myHostDocument;
      private VirtualFile myHostVirtualFile;
      private final InjectedLanguageManagerImpl myInjectedManager;
      private final PsiFile myHostPsiFile;

      public MyMultiHostRegistrar(Project project, InjectedLanguageManagerImpl injectedManager, PsiFile hostPsiFile) {
        myProject = project;
        myInjectedManager = injectedManager;
        myHostPsiFile = hostPsiFile;
        cleared = true;
      }

      @NotNull
      public MultiHostRegistrar startInjecting(@NotNull Language language) {
        relevantRangesInHostDocument = new SmartList<TextRange>();
        prefixes = new SmartList<String>();
        suffixes = new SmartList<String>();
        injectionHosts = new SmartList<PsiLanguageInjectionHost>();
        escapers = new SmartList<LiteralTextEscaper<PsiLanguageInjectionHost>>();
        shreds = new SmartList<PsiLanguageInjectionHost.Shred>();
        outChars = new StringBuilder();

        if (!cleared) {
          clear();
          throw new IllegalStateException("Seems you haven't called doneInjecting()");
        }
        ParserDefinition parserDefinition = language.getParserDefinition();
        if (parserDefinition == null) {
          throw new UnsupportedOperationException("Cannot inject language '" + language + "' since its getParserDefinition() returns null");
        }
        myLanguage = language;
        return this;
      }

      private void clear() {
        relevantRangesInHostDocument.clear();
        prefixes.clear();
        suffixes.clear();
        injectionHosts.clear();
        escapers.clear();
        shreds.clear();
        outChars.setLength(0);
        isOneLineEditor = false;
        myLanguage = null;

        cleared = true;
      }

      @NotNull
      public MultiHostRegistrar addPlace(@NonNls @Nullable String prefix,
                                         @NonNls @Nullable String suffix,
                                         @NotNull PsiLanguageInjectionHost host,
                                         @NotNull TextRange rangeInsideHost) {
        if (!host.getTextRange().contains(rangeInsideHost.shiftRight(host.getTextRange().getStartOffset()))) {
          clear();
          throw new IllegalArgumentException("rangeInsideHost must lie within host text range. rangeInsideHost:"+rangeInsideHost+"; host textRange:"+host.getTextRange());
        }
        if (myLanguage == null) {
          clear();
          throw new IllegalStateException("Seems you haven't called startInjecting()");
        }

        FileViewProvider viewProvider = myHostPsiFile.getViewProvider();
        myHostVirtualFile = viewProvider.getVirtualFile();
        myHostDocument = (DocumentEx)viewProvider.getDocument();
        myPsiManager = viewProvider.getManager();

        if (prefix == null) prefix = "";
        if (suffix == null) suffix = "";
        prefixes.add(prefix);
        suffixes.add(suffix);
        cleared = false;
        injectionHosts.add(host);
        outChars.append(prefix);
        LiteralTextEscaper<PsiLanguageInjectionHost> textEscaper = host.createLiteralTextEscaper();
        escapers.add(textEscaper);
        isOneLineEditor |= textEscaper.isOneLine();
        TextRange relevantRange = textEscaper.getRelevantTextRange().intersection(rangeInsideHost);
        if (relevantRange == null) return this;
        int startOffset = outChars.length();
        boolean result = textEscaper.decode(relevantRange, outChars);
        if (!result) return this;
        outChars.append(suffix);
        int endOffset = outChars.length();
        TextRange relevantRangeInHost = relevantRange.shiftRight(host.getTextRange().getStartOffset());
        relevantRangesInHostDocument.add(relevantRangeInHost);
        RangeMarker relevantMarker = myHostDocument.createRangeMarker(relevantRangeInHost);
        relevantMarker.setGreedyToLeft(true);
        relevantMarker.setGreedyToRight(true);
        shreds.add(new PsiLanguageInjectionHost.Shred(host, relevantMarker, prefix, suffix, new TextRange(startOffset, endOffset)));
        return this;
      }

      public void doneInjecting() {
        try {
          if (shreds.isEmpty()) {
            throw new IllegalStateException("Seems you haven't called addPlace()");
          }
          DocumentWindow documentWindow = new DocumentWindow(myHostDocument, isOneLineEditor, prefixes, suffixes, relevantRangesInHostDocument);
          VirtualFileWindow virtualFile = myInjectedManager.createVirtualFile(myLanguage, myHostVirtualFile, documentWindow, outChars);

          DocumentImpl decodedDocument = new DocumentImpl(outChars);
          FileDocumentManagerImpl.registerDocument(decodedDocument, virtualFile);

          SingleRootFileViewProvider viewProvider = new MyFileViewProvider(myProject, virtualFile, injectionHosts);
          ParserDefinition parserDefinition = myLanguage.getParserDefinition();
          assert parserDefinition != null;
          PsiFile psiFile = parserDefinition.createFile(viewProvider);
          assert psiFile.getViewProvider() instanceof MyFileViewProvider : psiFile.getViewProvider();

          SmartPsiElementPointer<PsiLanguageInjectionHost> pointer = createHostSmartPointer(injectionHosts.get(0));
          psiFile.putUserData(ResolveUtil.INJECTED_IN_ELEMENT, pointer);
          psiFile.putUserData(PsiManagerImpl.LANGUAGE_DIALECT, myLanguage instanceof LanguageDialect ? (LanguageDialect)myLanguage : null);

          final ASTNode parsedNode = psiFile.getNode();
          assert parsedNode instanceof FileElement : parsedNode;

          assert outChars.toString().equals(parsedNode.getText()) : outChars + "\n---\n" + parsedNode.getText() + "\n---\n";
          String documentText = documentWindow.getText();
          patchLeafs(parsedNode, escapers, shreds);
          assert parsedNode.getText().equals(documentText) : documentText + "\n---\n" + parsedNode.getText() + "\n---\n";

          parsedNode.putUserData(TreeElement.MANAGER_KEY, (PsiManagerEx)myPsiManager);
          virtualFile.setContent(null, documentWindow.getText(), false);
          FileDocumentManagerImpl.registerDocument(documentWindow, virtualFile);
          synchronized (PsiLock.LOCK) {
            psiFile = registerDocument(documentWindow, psiFile, shreds);
            MyFileViewProvider myFileViewProvider = (MyFileViewProvider)psiFile.getViewProvider();
            myFileViewProvider.setVirtualFile(virtualFile);
            myFileViewProvider.forceCachedPsi(psiFile);
          }

          List<Trinity<IElementType, PsiLanguageInjectionHost, TextRange>> tokens =
            obtainHighlightTokensFromLexer(myLanguage, outChars, escapers, shreds, virtualFile, documentWindow, myProject);
          psiFile.putUserData(HIGHLIGHT_TOKENS, tokens);
          PsiDocumentManagerImpl.checkConsistency(psiFile, documentWindow);

          Place place = new Place(psiFile, new ArrayList<PsiLanguageInjectionHost.Shred>(shreds));
          if (result == null) {
            result = new PlacesImpl();
          }
          result.add(place);
        }
        finally {
          clear();
        }
      }
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
  private static final Key<List<RangeMarker>> INJECTED_REGIONS_KEY = Key.create("INJECTED_REGIONS_KEY");
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
    List<RangeMarker> injected = getCachedInjectedRegions(hostDocument);
    if (injected.isEmpty()) return;

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    PsiFile hostPsiFile = documentManager.getPsiFile(hostDocument);
    assert hostPsiFile != null;
    for (RangeMarker rangeMarker : injected) {
      PsiElement element = rangeMarker.isValid() ? hostPsiFile.findElementAt(rangeMarker.getStartOffset()) : null;
      if (element == null) {
        injected.remove(rangeMarker);
        continue;
      }
      // it is here reparse happens and old file contents replaced
      enumerate(element, hostPsiFile, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          PsiDocumentManagerImpl.checkConsistency(injectedPsi, injectedPsi.getViewProvider().getDocument());
        }
      }, true);
    }
    PsiDocumentManagerImpl.checkConsistency(hostPsiFile, hostDocument);
  }

  public static void clearCaches(PsiFile injected) {
    VirtualFileWindow virtualFile = (VirtualFileWindow)injected.getVirtualFile();
    ((PsiManagerEx)injected.getManager()).getFileManager().setViewProvider(virtualFile, null);
    InjectedLanguageManagerImpl.getInstanceImpl(injected.getProject()).clearCaches(virtualFile);
  }

  private static PsiFile registerDocument(final DocumentWindow documentWindow, final PsiFile injectedPsi,
                                          List<PsiLanguageInjectionHost.Shred> shreds) {
    DocumentEx hostDocument = documentWindow.getDelegate();
    List<DocumentWindow> injected = getCachedInjectedDocuments(hostDocument);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(injectedPsi.getProject());
    for (int i = injected.size()-1; i>=0; i--) {
      DocumentWindow oldDocument = injected.get(i);
      PsiFileImpl oldFile = (PsiFileImpl)documentManager.getCachedPsiFile(oldDocument);
      FileViewProvider oldViewProvider;

      if (oldFile == null || !oldFile.isValid() || !((oldViewProvider = oldFile.getViewProvider()) instanceof MyFileViewProvider) || !((MyFileViewProvider)oldViewProvider).isValid()) {
        injected.remove(i);
        oldDocument.dispose();
        continue;
      }

      ASTNode injectedNode = injectedPsi.getNode();
      ASTNode oldFileNode = oldFile.getNode();
      assert injectedNode != null;
      assert oldFileNode != null;
      if (oldDocument.areRangesEqual(documentWindow)) {
        if (oldFile.getFileType() != injectedPsi.getFileType()) {
          injected.remove(i);
          oldDocument.dispose();
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

          MyFileViewProvider viewProvider = (MyFileViewProvider)oldViewProvider;
          viewProvider.replace(injectedPsi, shreds);
        }
        return oldFile;
      }
    }
    injected.add(documentWindow);
    List<RangeMarker> injectedRegions = getCachedInjectedRegions(hostDocument);
    RangeMarker newMarker = documentWindow.getHostRanges()[0];
    TextRange newRange = new TextRange(newMarker.getStartOffset(), newMarker.getEndOffset());
    for (int i = 0; i < injectedRegions.size(); i++) {
      RangeMarker stored = injectedRegions.get(i);
      TextRange storedRange = new TextRange(stored.getStartOffset(), stored.getEndOffset());
      if (storedRange.intersects(newRange)) {
        injectedRegions.set(i, newMarker);
        break;
      }
      if (storedRange.getStartOffset() > newRange.getEndOffset()) {
        injectedRegions.add(i, newMarker);
        break;
      }
    }
    if (injectedRegions.isEmpty() || newRange.getStartOffset() > injectedRegions.get(injectedRegions.size()-1).getEndOffset()) {
      injectedRegions.add(newMarker);
    }
    return injectedPsi;
  }

  private static List<RangeMarker> getCachedInjectedRegions(Document hostDocument) {
    List<RangeMarker> injectedRegions = hostDocument.getUserData(INJECTED_REGIONS_KEY);
    if (injectedRegions == null) {
      injectedRegions = ((UserDataHolderEx)hostDocument).putUserDataIfAbsent(INJECTED_REGIONS_KEY, new CopyOnWriteArrayList<RangeMarker>());
    }
    return injectedRegions;
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

  public static PsiFile getTopLevelFile(PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    Document document = PsiDocumentManager.getInstance(element.getProject()).getCachedDocument(containingFile);
    if (document instanceof DocumentWindow) {
      PsiElement host = containingFile.getContext();
      if (host != null) containingFile = host.getContainingFile();
    }
    return containingFile;
  }
  public static boolean isInInjectedLanguagePrefixSuffix(final PsiElement element) {
    PsiFile injectedFile = element.getContainingFile();
    if (injectedFile == null) return false;
    Document document = PsiDocumentManager.getInstance(element.getProject()).getCachedDocument(injectedFile);
    if (!(document instanceof DocumentWindow)) return false;
    DocumentWindow documentWindow = (DocumentWindow)document;
    TextRange elementRange = element.getTextRange();
    TextRange editable = documentWindow.intersectWithEditable(elementRange);
    return !elementRange.equals(editable); //) throw new IncorrectOperationException("Can't change "+ UsageViewUtil.createNodeText(element, true));
  }
}
