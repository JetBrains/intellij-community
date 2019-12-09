// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInspection.htmlInspections.RenameTagBeginOrEndIntentionAction;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.core.impl.PomModelImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;

public final class XmlTagNameSynchronizer implements CommandListener, EditorFactoryListener {
  private static final Key<Boolean> SKIP_COMMAND = Key.create("tag.name.synchronizer.skip.command");
  private static final Logger LOG = Logger.getInstance(XmlTagNameSynchronizer.class);
  private static final Set<Language> SUPPORTED_LANGUAGES = ContainerUtil.set(HTMLLanguage.INSTANCE,
                                                                             XMLLanguage.INSTANCE,
                                                                             XHTMLLanguage.INSTANCE);

  private static final Key<TagNameSynchronizer> SYNCHRONIZER_KEY = Key.create("tag_name_synchronizer");

  private XmlTagNameSynchronizer() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(CommandListener.TOPIC, this);
  }

  @Override
  public void editorCreated(@NotNull EditorFactoryEvent event) {
    Editor editor = event.getEditor();
    Project project = editor.getProject();
    if (project == null || !(editor instanceof EditorImpl)) {
      return;
    }

    Document document = editor.getDocument();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    Language language = findXmlLikeLanguage(project, file);
    if (language != null) {
      new TagNameSynchronizer((EditorImpl)editor, project, language).listenForDocumentChanges();
    }
  }

  private static Language findXmlLikeLanguage(Project project, VirtualFile file) {
    final PsiFile psiFile = file != null && file.isValid() ? PsiManager.getInstance(project).findFile(file) : null;
    if (psiFile != null) {
      for (Language language : psiFile.getViewProvider().getLanguages()) {
        if ((ContainerUtil.find(SUPPORTED_LANGUAGES, language::isKindOf) != null || HtmlUtil.supportsXmlTypedHandlers(psiFile)) &&
            !(language instanceof TemplateLanguage)) {
          return language;
        }
      }
    }
    return null;
  }

  @NotNull
  private static TagNameSynchronizer[] findSynchronizers(final Document document) {
    if (!WebEditorOptions.getInstance().isSyncTagEditing() || document == null) return TagNameSynchronizer.EMPTY;
    final Editor[] editors = EditorFactory.getInstance().getEditors(document);

    return ContainerUtil.mapNotNull(editors, editor -> editor.getUserData(SYNCHRONIZER_KEY), TagNameSynchronizer.EMPTY);
  }

  @Override
  public void beforeCommandFinished(@NotNull CommandEvent event) {
    final TagNameSynchronizer[] synchronizers = findSynchronizers(event.getDocument());
    for (TagNameSynchronizer synchronizer : synchronizers) {
      synchronizer.beforeCommandFinished();
    }
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

  private static class TagNameSynchronizer implements DocumentListener {
    private static final Key<Couple<RangeMarker>> MARKERS_KEY = Key.create("tag.name.synchronizer.markers");
    private static final TagNameSynchronizer[] EMPTY = new TagNameSynchronizer[0];
    private final PsiDocumentManagerBase myDocumentManager;
    private final Language myLanguage;
    private final EditorImpl myEditor;
    private boolean myApplying;

    private TagNameSynchronizer(EditorImpl editor, Project project, Language language) {
      myEditor = editor;
      myLanguage = language;
      myDocumentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
    }

    private void listenForDocumentChanges() {
      final Disposable disposable = myEditor.getDisposable();
      final Document document = myEditor.getDocument();
      document.addDocumentListener(this, disposable);
      myEditor.putUserData(SYNCHRONIZER_KEY, this);
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
        if (!XmlUtil.isValidTagNameChar(fragment.charAt(i))) {
          clearMarkers(caret);
          return;
        }
      }

      Couple<RangeMarker> markers = caret.getUserData(MARKERS_KEY);
      if (markers != null && !fitsInMarker(markers, offset, oldLength)) {
        clearMarkers(caret);
        markers = null;
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
        caret.putUserData(MARKERS_KEY, markers);
      }
    }

    private static boolean fitsInMarker(Couple<RangeMarker> markers, int offset, int oldLength) {
      RangeMarker leader = markers.first;
      return leader.isValid() && offset >= leader.getStartOffset() && offset + oldLength <= leader.getEndOffset();
    }

    private static void clearMarkers(Caret caret) {
      Couple<RangeMarker> markers = caret.getUserData(MARKERS_KEY);
      if (markers != null) {
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
          if (!XmlUtil.isValidTagNameChar(c)) break;
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
        if (!XmlUtil.isValidTagNameChar(c) || seenColon && c == ':') {
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
        Couple<RangeMarker> markers = caret.getUserData(MARKERS_KEY);
        if (markers == null || !markers.first.isValid() || !markers.second.isValid()) return;
        final Document document = myEditor.getDocument();
        final Runnable apply = () -> {
          final RangeMarker leader = markers.first;
          final RangeMarker support = markers.second;
          if (document.getTextLength() < leader.getEndOffset()) {
            return;
          }
          final String name = document.getText(new TextRange(leader.getStartOffset(), leader.getEndOffset()));
          if (document.getTextLength() >= support.getEndOffset() &&
              !name.equals(document.getText(new TextRange(support.getStartOffset(), support.getEndOffset())))) {
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

    private RangeMarker findSupport(RangeMarker leader, PsiFile file, Document document) {
      final int offset = leader.getStartOffset();
      PsiElement element = InjectedLanguageUtil.findElementAtNoCommit(file, offset);
      PsiElement support = findSupportElement(element);
      if (support == null && file.getViewProvider() instanceof MultiplePsiFilesPerDocumentFileViewProvider) {
        element = file.getViewProvider().findElementAt(offset, myLanguage);
        support = findSupportElement(element);
      }

      if (support == null) return findSupportForTagList(leader, element, document);

      final TextRange range = support.getTextRange();
      TextRange realRange = InjectedLanguageManager.getInstance(file.getProject()).injectedToHost(element.getContainingFile(), range);
      return document.createRangeMarker(realRange.getStartOffset(), realRange.getEndOffset(), true);
    }

    private static RangeMarker findSupportForTagList(RangeMarker leader, PsiElement element, Document document) {
      if (leader.getStartOffset() != leader.getEndOffset() || element == null) return null;

      PsiElement support = null;
      if ("<>".equals(element.getText())) {
        PsiElement last = element.getParent().getLastChild();
        if ("</>".equals(last.getText())) {
          support = last;
        }
      }
      if ("</>".equals(element.getText())) {
        PsiElement first = element.getParent().getFirstChild();
        if ("<>".equals(first.getText())) {
          support = first;
        }
      }
      if (support != null) {
        TextRange range = support.getTextRange();
        return document.createRangeMarker(range.getEndOffset() - 1, range.getEndOffset() - 1, true);
      }
      return null;
    }

    private static PsiElement findSupportElement(PsiElement element) {
      if (element == null || TreeUtil.findSibling(element.getNode(), XmlTokenType.XML_TAG_END) == null) return null;
      PsiElement support = RenameTagBeginOrEndIntentionAction.findOtherSide(element, false);
      support = support == null || element == support ? RenameTagBeginOrEndIntentionAction.findOtherSide(element, true) : support;
      return support != null && StringUtil.equals(element.getText(), support.getText()) ? support : null;
    }
  }
}
