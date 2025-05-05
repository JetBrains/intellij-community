// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInspection.htmlInspections.RenameTagBeginOrEndIntentionAction;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.core.impl.PomModelImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.doIfNotNull;
import static com.intellij.util.ObjectUtils.tryCast;

public final class XmlTagNameSynchronizer implements EditorFactoryListener {
  private static final Key<Boolean> SKIP_COMMAND = Key.create("tag.name.synchronizer.skip.command");
  private static final Logger LOG = Logger.getInstance(XmlTagNameSynchronizer.class);
  private static final Set<Language> SUPPORTED_LANGUAGES = Set.of(HTMLLanguage.INSTANCE,
                                                                  XMLLanguage.INSTANCE,
                                                                  XHTMLLanguage.INSTANCE);

  private static final Key<TagNameSynchronizer> SYNCHRONIZER_KEY = Key.create("tag_name_synchronizer");

  private XmlTagNameSynchronizer() { }

  private static void createSynchronizerFor(Editor editor) {
    Project project = editor.getProject();
    if (project == null || !(editor instanceof EditorImpl)) {
      return;
    }
    ReadAction.nonBlocking((Callable<Void>)() -> {
      if (editor.isDisposed() || project.isDisposed())
        return null;
      Document document = editor.getDocument();
      VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      Language language = findXmlLikeLanguage(project, file);
      if (language != null) {
        new TagNameSynchronizer((EditorImpl)editor, project, language).listenForDocumentChanges();
      }
      return null;
    })
      .coalesceBy(Pair.create(editor, XmlTagNameSynchronizer.class))
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private static void recreateSynchronizers() {
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      TagNameSynchronizer synchronizer = editor.getUserData(SYNCHRONIZER_KEY);
      if (synchronizer != null) {
        Disposer.dispose(synchronizer);
      }
      createSynchronizerFor(editor);
    }
  }

  private static @NotNull Stream<TagNameSynchronizer> findSynchronizers(@Nullable Document document) {
    if (document == null || !WebEditorOptions.getInstance().isSyncTagEditing()) {
      return Stream.empty();
    }
    return EditorFactory.getInstance().editors(document, null)
      .map(editor -> editor.getUserData(SYNCHRONIZER_KEY))
      .filter(Objects::nonNull);
  }

  private static Language findXmlLikeLanguage(Project project, VirtualFile file) {
    final PsiFile psiFile = file != null && file.isValid() ? PsiManager.getInstance(project).findFile(file) : null;
    if (psiFile != null) {
      for (Language language : psiFile.getViewProvider().getLanguages()) {
        if (
          (ContainerUtil.find(SUPPORTED_LANGUAGES, language::isKindOf) != null)
          && !(language instanceof TemplateLanguage)
          && !(language instanceof ExternallyTagSynchronizedLanguage)
          || XmlTypedHandlersAdditionalSupport.supportsTypedHandlers(psiFile, language)
        ) {
          return language;
        }
      }
    }
    return null;
  }

  public static void runWithoutCancellingSyncTagsEditing(@NotNull Document document, @NotNull Runnable runnable) {
    document.putUserData(SKIP_COMMAND, Boolean.TRUE);
    try {
      runnable.run();
    }
    finally {
      document.putUserData(SKIP_COMMAND, null);
    }
  }

  public static class MyEditorFactoryListener implements EditorFactoryListener {
    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
      ReadAction.compute(() -> { createSynchronizerFor(event.getEditor()); return null; });
    }
  }

  static final class MyCommandListener implements CommandListener {
    @Override
    public void beforeCommandFinished(@NotNull CommandEvent event) {
      findSynchronizers(event.getDocument()).forEach(synchronizer -> synchronizer.beforeCommandFinished());
    }
  }

  public static class MyDynamicPluginListener implements DynamicPluginListener {
    @Override
    public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
      recreateSynchronizers();
    }

    @Override
    public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
      recreateSynchronizers();
    }
  }

  private static final class TagNameSynchronizer implements DocumentListener, CaretListener, Disposable {
    private static final Key<Couple<RangeMarker>> MARKERS_KEY = Key.create("tag.name.synchronizer.markers");
    private static final TreeSet<Segment> allMarkers = new TreeSet<>(Comparator.comparingInt(Segment::getStartOffset));
    private final PsiDocumentManagerBase myDocumentManager;
    private final Language myLanguage;
    private final EditorImpl myEditor;
    private final Project myProject;
    private boolean myApplying;

    private TagNameSynchronizer(EditorImpl editor, Project project, Language language) {
      myEditor = editor;
      myLanguage = language;
      myDocumentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
      myProject = project;
    }

    @Override
    public void dispose() {
      myEditor.putUserData(SYNCHRONIZER_KEY, null);
      allMarkers.clear();
    }

    private void listenForDocumentChanges() {
      Disposer.register(myEditor.getDisposable(), this);
      myEditor.getDocument().addDocumentListener(this, this);
      myEditor.getCaretModel().addCaretListener(this, this);
      myEditor.putUserData(SYNCHRONIZER_KEY, this);
      for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
        Couple<RangeMarker> markers = getMarkers(caret);
        if (markers != null) {
          allMarkers.add(markers.first);
          allMarkers.add(markers.second);
        }
      }
    }

    @Override
    public void caretRemoved(@NotNull CaretEvent event) {
      Caret caret = event.getCaret();
      clearMarkers(caret);
    }

    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
      if (!WebEditorOptions.getInstance().isSyncTagEditing()) return;

      final Document document = event.getDocument();
      Project project = Objects.requireNonNull(myEditor.getProject());
      if (myApplying || project.isDefault() || UndoManager.getInstance(project).isUndoInProgress() ||
          !PomModelImpl.isAllowPsiModification() || document.isInBulkUpdate()) {
        return;
      }

      final int offset = event.getOffset();
      final int oldLength = event.getOldLength();
      final CharSequence fragment = event.getNewFragment();
      final int newLength = event.getNewLength();

      if (document.getUserData(SKIP_COMMAND) == Boolean.TRUE) {
        // xml completion inserts extra space after tag name to ensure correct parsing
        // js auto-import may change beginning of the document when component is imported
        // we need to ignore it
        return;
      }

      Caret caret = myEditor.getCaretModel().getCurrentCaret();

      for (int i = 0; i < newLength; i++) {
        if (!isValidTagNameChar(fragment.charAt(i))) {
          clearMarkers(caret);
          return;
        }
      }

      Couple<RangeMarker> markers = getMarkers(caret);
      if (markers != null && !fitsInMarker(markers, offset, oldLength)) {
        clearMarkers(caret);
        markers = null;
      }

      int caretOffset = caret.getOffset();
      Segment floor = allMarkers.floor(new TextRange(caretOffset, caretOffset));
      // Skip markers creation if cursors cover same tag area as other cursors
      if (floor != null
          && (markers == null || (floor != markers.second && floor != markers.first))
          && caretOffset <= floor.getEndOffset()) {
        clearMarkers(caret);
        return;
      }

      if (markers == null) {
        final PsiFile file = myDocumentManager.getPsiFile(document);
        if (file == null || myDocumentManager.getSynchronizer().isInSynchronization(document)) return;

        final RangeMarker leader = createTagNameMarker(caret);
        if (leader == null) return;
        leader.setGreedyToLeft(true);
        leader.setGreedyToRight(true);

        if (myDocumentManager.isUncommited(document)) {
          myDocumentManager.commitDocument(document);
        }

        final RangeMarker support = findSupport(leader, file, document);
        if (support == null) return;
        support.setGreedyToLeft(true);
        support.setGreedyToRight(true);
        markers = Couple.of(leader, support);
        if (!fitsInMarker(markers, offset, oldLength)) return;
        setMarkers(caret, markers);
      }
    }

    private static boolean fitsInMarker(Couple<RangeMarker> markers, int offset, int oldLength) {
      RangeMarker leader = markers.first;
      return leader.isValid() && offset >= leader.getStartOffset() && offset + oldLength <= leader.getEndOffset();
    }

    private static Couple<RangeMarker> getMarkers(Caret caret) {
      return caret.getUserData(MARKERS_KEY);
    }

    private static void setMarkers(Caret caret, Couple<RangeMarker> markers) {
      caret.putUserData(MARKERS_KEY, markers);
      allMarkers.add(markers.first);
      allMarkers.add(markers.second);
    }

    private static void clearMarkers(Caret caret) {
      Couple<RangeMarker> markers = caret.getUserData(MARKERS_KEY);
      if (markers != null) {
        allMarkers.remove(markers.first);
        allMarkers.remove(markers.second);
        markers.first.dispose();
        markers.second.dispose();
        caret.putUserData(MARKERS_KEY, null);
      }
    }

    private RangeMarker createTagNameMarker(Caret caret) {
      final int offset = caret.getOffset();
      final Document document = myEditor.getDocument();
      final CharSequence sequence = document.getCharsSequence();
      int start = -1;
      boolean seenColon = false;
      for (int i = offset - 1; i >= Math.max(0, offset - 50); i--) {
        try {
          final char c = sequence.charAt(i);
          if (c == '<' || c == '/' && i > 0 && sequence.charAt(i - 1) == '<') {
            start = i + 1;
            break;
          }
          if (!isValidTagNameChar(c)) break;
          seenColon |= c == ':';
        }
        catch (IndexOutOfBoundsException e) {
          LOG.error("incorrect offset:" + i + ", initial: " + offset, new Attachment("document.txt", sequence.toString()));
          return null;
        }
      }
      if (start < 0) return null;
      int end = -1;
      for (int i = offset; i < Math.min(document.getTextLength(), offset + 50); i++) {
        final char c = sequence.charAt(i);
        if (!isValidTagNameChar(c) || seenColon && c == ':') {
          end = i;
          break;
        }
        seenColon |= c == ':';
      }
      if (end < 0 || start > end) return null;
      return document.createRangeMarker(start, end, true);
    }

    void beforeCommandFinished() {
      CaretAction action = caret -> {
        Couple<RangeMarker> markers = getMarkers(caret);
        final Document document = myEditor.getDocument();
        if (markers == null
            || !markers.first.isValid()
            || !markers.second.isValid()
            || getNameToReplace(document, markers) == null) {
          return;
        }

        final Runnable apply = () -> {
          final String name = getNameToReplace(document, markers);
          if (name != null) {
            final RangeMarker support = markers.second;
            document.replaceString(support.getStartOffset(), support.getEndOffset(), name);
          }
        };
        ApplicationManager.getApplication().runWriteAction(() -> {
          final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
          if (lookup != null) {
            lookup.performGuardedChange(apply);
          }
          else {
            apply.run();
          }
        });
      };
      myApplying = true;
      try {
        if (myEditor.getCaretModel().isIteratingOverCarets()) {
          action.perform(myEditor.getCaretModel().getCurrentCaret());
        }
        else {
          myEditor.getCaretModel().runForEachCaret(action);
        }
      }
      finally {
        myApplying = false;
      }
    }

    private static @Nullable String getNameToReplace(final Document document, @NotNull Couple<RangeMarker> markers) {
      final RangeMarker leader = markers.first;
      final RangeMarker support = markers.second;
      if (document.getTextLength() < leader.getEndOffset()) {
        return null;
      }
      final String name = document.getText(leader.getTextRange());
      if (document.getTextLength() >= support.getEndOffset() &&
          !name.equals(document.getText(support.getTextRange()))) {
        return name;
      }
      return null;
    }

    private RangeMarker findSupport(RangeMarker leader, PsiFile file, Document document) {
      final TextRange leaderRange = leader.getTextRange();
      final int offset = leader.getStartOffset();
      PsiElement element = findNameElement(InjectedLanguageUtilBase.findElementAtNoCommit(file, offset));
      TextRange support = findSupportRange(element);
      if (!isSupportRangeValid(document, leaderRange, support) &&
          file.getViewProvider() instanceof MultiplePsiFilesPerDocumentFileViewProvider) {
        element = findNameElement(file.getViewProvider().findElementAt(offset, myLanguage));
        support = findSupportRange(element);
      }

      if (!isSupportRangeValid(document, leaderRange, support)) return findSupportForEmptyTag(leader, element, document);
      return document.createRangeMarker(support.getStartOffset(), support.getEndOffset(), true);
    }

    private static PsiElement findNameElement(@Nullable PsiElement element) {
      return element instanceof OuterLanguageElement ? TemplateLanguageUtil.getSameLanguageTreeNext(element) : element;
    }

    private boolean isValidTagNameChar(char c) {
      if (XmlUtil.isValidTagNameChar(c)) return true;
      final XmlExtension extension = getXmlExtension();
      if (extension == null) return false;
      return extension.isValidTagNameChar(c);
    }

    private @Nullable XmlExtension getXmlExtension() {
      Document document = myEditor.getDocument();
      VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      PsiFile psiFile = file != null && file.isValid() ? PsiManager.getInstance(myProject).findFile(file) : null;
      if (psiFile == null) {
        return null;
      }
      return XmlExtension.getExtension(psiFile);
    }

    private static RangeMarker findSupportForEmptyTag(RangeMarker leader, PsiElement element, Document document) {
      int offset = leader.getStartOffset();
      if (offset != leader.getEndOffset() || element == null || offset == 0) return null;

      XmlTag tag = tryCast(element.getParent(), XmlTag.class);
      CharSequence contents = document.getCharsSequence();
      if (tag != null && tag.getName().isEmpty()) {
        // Support JSX empty tags
        PsiElement startTag = tag.getFirstChild();
        PsiElement endTag = tag.getLastChild();
        if (endTag instanceof XmlToken && endTag.getNode().getElementType() == XmlTokenType.XML_TAG_END) {
          endTag = PsiTreeUtil.skipWhitespacesBackward(endTag);
        }
        if (startTag != endTag && startTag instanceof LeafPsiElement && endTag instanceof LeafPsiElement) {
          int startTagOffset = startTag.getNode().getStartOffset();
          int endTagOffset = endTag.getNode().getStartOffset();
          if (startTagOffset + 1 == offset) {
            return contents.charAt(endTagOffset) == '<' && contents.charAt(endTagOffset + 1) == '/'
                   ? document.createRangeMarker(endTagOffset + 2, endTagOffset + 2, true)
                   : null;
          }
          else if (endTagOffset + 2 == offset) {
            return contents.charAt(startTagOffset) == '<'
                   ? document.createRangeMarker(startTagOffset + 1, startTagOffset + 1, true)
                   : null;
          }
        }
        return null;
      }
      TextRange range = doIfNotNull(doIfNotNull(element.getParent(), PsiElement::getParent), PsiElement::getTextRange);
      if (range == null) return null;
      int length = contents.length();
      char prev = contents.charAt(offset - 1);
      if (prev == '<') {
        int nextCharPos = StringUtil.skipWhitespaceForward(contents, offset);
        if (nextCharPos >= 0 && nextCharPos < length && contents.charAt(nextCharPos) == '>') {
          // We look for the next opening tag and start to search for the closing empty tag after the end of the next tag's pair
          int nextTagStart = StringUtil.indexOf(contents, '<', nextCharPos, range.getEndOffset());
          int nextTagFirstChar = contents.charAt(StringUtil.skipWhitespaceForward(contents, nextTagStart + 1));

          int searchStart = nextCharPos;
          if (nextTagFirstChar != '/' && nextTagFirstChar != '>') {
            PsiFile file = element.getContainingFile();
            TextRange nextTagsPair = findSupportRange(findNameElement(InjectedLanguageUtilBase.findElementAtNoCommit(file, nextTagStart)));
            if (nextTagsPair != null && nextTagsPair.getEndOffset() > nextCharPos) {
              searchStart = nextTagsPair.getEndOffset();
            }
          }

          int endTagStart = StringUtil.indexOf(contents, '<', searchStart, range.getEndOffset());
          int endTagEnd = endTagStart > 0 && endTagStart < length - 1 && contents.charAt(endTagStart + 1) == '/'
                          ? StringUtil.skipWhitespaceForward(contents, endTagStart + 2) : -1;
          if (endTagEnd > 0 && endTagEnd < length && contents.charAt(endTagEnd) == '>') {
            // Sync with it
            return document.createRangeMarker(endTagStart + 2, endTagStart + 2, true);
          }
          else {
            // Otherwise create a self pointing sync to avoid code destruction
            return document.createRangeMarker(offset, offset, true);
          }
        }
      }
      else if (prev == '/' && offset >= 2 && contents.charAt(offset - 2) == '<') {
        int nextCharPos = StringUtil.skipWhitespaceForward(contents, offset);
        if (nextCharPos >= 0 && nextCharPos < length && contents.charAt(nextCharPos) == '>') {
          // Try to find matching empty opening tag
          int startTagEnd = StringUtil.lastIndexOf(contents, '>', range.getStartOffset(), offset - 2);
          int startTagStart = startTagEnd > 0 ? StringUtil.skipWhitespaceBackward(contents, startTagEnd) - 1 : -1;
          if (startTagStart > 0 && startTagStart < length && contents.charAt(startTagStart) == '<') {
            return document.createRangeMarker(startTagStart + 1, startTagStart + 1, true);
          }
          else {
            // Otherwise create a self pointing sync to avoid code destruction
            return document.createRangeMarker(offset, offset, true);
          }
        }
      }
      return null;
    }

    private static boolean isSupportRangeValid(@NotNull Document document, @NotNull TextRange leader, @Nullable TextRange support) {
      if (support == null) return false;
      return document.getText(leader).equals(document.getText(support));
    }

    private static @Nullable TextRange findSupportRange(@Nullable PsiElement leader) {
      if (leader == null || TreeUtil.findSibling(leader.getNode(), XmlTokenType.XML_TAG_END) == null) return null;
      PsiElement support = RenameTagBeginOrEndIntentionAction.findOtherSide(leader, false);
      if (support == null || leader == support) support = RenameTagBeginOrEndIntentionAction.findOtherSide(leader, true);
      if (support == null) return null;
      final int start = findSupportRangeStart(support);
      final int end = findSupportRangeEnd(support);
      final TextRange supportRange = TextRange.create(start, end);
      return InjectedLanguageManager.getInstance(leader.getProject()).injectedToHost(leader.getContainingFile(), supportRange);
    }

    private static int findSupportRangeStart(@NotNull PsiElement support) {
      PsiElement current = support;
      while (current.getPrevSibling() instanceof OuterLanguageElement) {
        current = current.getPrevSibling();
      }

      return current.getTextRange().getStartOffset();
    }

    private static int findSupportRangeEnd(@NotNull PsiElement support) {
      PsiElement current = support;
      while (current.getNextSibling() instanceof OuterLanguageElement) {
        current = current.getNextSibling();
      }

      return current.getTextRange().getEndOffset();
    }
  }
}
