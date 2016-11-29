/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.completion.XmlTagInsertHandler;
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
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
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
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author Dennis.Ushakov
 */
public class XmlTagNameSynchronizer extends CommandAdapter implements NamedComponent {
  private static final Logger LOG = Logger.getInstance(XmlTagNameSynchronizer.class);
  private static final Set<Language> SUPPORTED_LANGUAGES = ContainerUtil.set(HTMLLanguage.INSTANCE,
                                                                             XMLLanguage.INSTANCE,
                                                                             XHTMLLanguage.INSTANCE);

  private static final Key<TagNameSynchronizer> SYNCHRONIZER_KEY = Key.create("tag_name_synchronizer");
  private final FileDocumentManager myFileDocumentManager;

  public XmlTagNameSynchronizer(EditorFactory editorFactory, FileDocumentManager manager, CommandProcessor processor) {
    myFileDocumentManager = manager;
    editorFactory.addEditorFactoryListener(new EditorFactoryAdapter() {
      @Override
      public void editorCreated(@NotNull EditorFactoryEvent event) {
        installSynchronizer(event.getEditor());
      }
    }, ApplicationManager.getApplication());
    processor.addCommandListener(this);
  }

  private void installSynchronizer(final Editor editor) {
    final Project project = editor.getProject();
    if (project == null) return;

    final Document document = editor.getDocument();
    final VirtualFile file = myFileDocumentManager.getFile(document);
    final Language language = findXmlLikeLanguage(project, file);
    if (language != null) new TagNameSynchronizer(editor, project, language);
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
  @Override
  public String getComponentName() {
    return "XmlTagNameSynchronizer";
  }

  @NotNull
  private static TagNameSynchronizer[] findSynchronizers(final Document document) {
    if (!WebEditorOptions.getInstance().isSyncTagEditing() || document == null) return TagNameSynchronizer.EMPTY;
    final Editor[] editors = EditorFactory.getInstance().getEditors(document);

    return ContainerUtil.mapNotNull(editors, editor -> editor.getUserData(SYNCHRONIZER_KEY), TagNameSynchronizer.EMPTY);
  }

  @Override
  public void beforeCommandFinished(CommandEvent event) {
    final TagNameSynchronizer[] synchronizers = findSynchronizers(event.getDocument());
    for (TagNameSynchronizer synchronizer : synchronizers) {
      synchronizer.beforeCommandFinished();
    }
  }

  private static class TagNameSynchronizer extends DocumentAdapter {
    public static final TagNameSynchronizer[] EMPTY = new TagNameSynchronizer[0];
    private final PsiDocumentManagerBase myDocumentManager;
    private final Language myLanguage;

    private enum State {INITIAL, TRACKING, APPLYING}

    private final Editor myEditor;
    private State myState = State.INITIAL;
    private final List<Couple<RangeMarker>> myMarkers = new SmartList<>();

    public TagNameSynchronizer(Editor editor, Project project, Language language) {
      myEditor = editor;
      myLanguage = language;
      final Disposable disposable = ((EditorImpl)editor).getDisposable();
      final Document document = editor.getDocument();
      document.addDocumentListener(this, disposable);
      editor.putUserData(SYNCHRONIZER_KEY, this);
      myDocumentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
    }

    @Override
    public void beforeDocumentChange(DocumentEvent event) {
      if (!WebEditorOptions.getInstance().isSyncTagEditing()) return;

      final Document document = event.getDocument();
      if (myState == State.APPLYING || UndoManager.getInstance(myEditor.getProject()).isUndoInProgress() ||
          !PomModelImpl.isAllowPsiModification() || ((DocumentEx)document).isInBulkUpdate()) {
        return;
      }

      final int offset = event.getOffset();
      final int oldLength = event.getOldLength();
      final CharSequence fragment = event.getNewFragment();
      final int newLength = event.getNewLength();

      if (document.getUserData(XmlTagInsertHandler.ENFORCING_TAG) == Boolean.TRUE) {
        // xml completion inserts extra space after tag name to ensure correct parsing
        // we need to ignore it
        return;
      }

      for (int i = 0; i < newLength; i++) {
        if (!XmlUtil.isValidTagNameChar(fragment.charAt(i))) {
          clearMarkers();
          return;
        }
      }

      if (myState == State.INITIAL) {
        final PsiFile file = myDocumentManager.getPsiFile(document);
        if (file == null || myDocumentManager.getSynchronizer().isInSynchronization(document)) return;

        final SmartList<RangeMarker> leaders = new SmartList<>();
        for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
          final RangeMarker leader = createTagNameMarker(caret);
          if (leader == null) {
            for (RangeMarker marker : leaders) {
              marker.dispose();
            }
            return;
          }
          leader.setGreedyToLeft(true);
          leader.setGreedyToRight(true);
          leaders.add(leader);
        }
        if (leaders.isEmpty()) return;

        if (myDocumentManager.isUncommited(document)) {
          myDocumentManager.commitDocument(document);
        }

        for (RangeMarker leader : leaders) {
          final RangeMarker support = findSupport(leader, file, document);
          if (support == null) {
            clearMarkers();
            return;
          }
          support.setGreedyToLeft(true);
          support.setGreedyToRight(true);
          myMarkers.add(Couple.of(leader, support));
        }

        if (!fitsInMarker(offset, oldLength)) {
          clearMarkers();
          return;
        }

        myState = State.TRACKING;
      }
      if (myMarkers.isEmpty()) return;

      boolean fitsInMarker = fitsInMarker(offset, oldLength);
      if (!fitsInMarker || myMarkers.size() != myEditor.getCaretModel().getCaretCount()) {
        clearMarkers();
        beforeDocumentChange(event);
      }
    }

    public boolean fitsInMarker(int offset, int oldLength) {
      boolean fitsInMarker = false;
      for (Couple<RangeMarker> leaderAndSupport : myMarkers) {
        final RangeMarker leader = leaderAndSupport.first;
        if (!leader.isValid()) {
          fitsInMarker = false;
          break;
        }
        fitsInMarker |= offset >= leader.getStartOffset() && offset + oldLength <= leader.getEndOffset();
      }
      return fitsInMarker;
    }

    public void clearMarkers() {
      for (Couple<RangeMarker> leaderAndSupport : myMarkers) {
        leaderAndSupport.first.dispose();
        leaderAndSupport.second.dispose();
      }
      myMarkers.clear();
      myState = State.INITIAL;
    }

    private RangeMarker createTagNameMarker(Caret caret) {
      final int offset = caret.getOffset();
      final Document document = myEditor.getDocument();
      final CharSequence sequence = document.getCharsSequence();
      int start = -1;
      int end = -1;
      boolean seenColon = false;
      for (int i = offset - 1; i >= Math.max(0, offset - 50); i--) {
        try {
          final char c = sequence.charAt(i);
          if (c == '<' || (c == '/' && i > 0 && sequence.charAt(i - 1) == '<')) {
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
      for (int i = offset; i < Math.min(document.getTextLength(), offset + 50); i++) {
        final char c = sequence.charAt(i);
        if (!XmlUtil.isValidTagNameChar(c) || (seenColon && c == ':')) {
          end = i;
          break;
        }
        seenColon |= c == ':';
      }
      if (end < 0 || start >= end) return null;
      return document.createRangeMarker(start, end, true);
    }

    public void beforeCommandFinished() {
      if (myMarkers.isEmpty()) return;

      myState = State.APPLYING;

      final Document document = myEditor.getDocument();
      final Runnable apply = () -> {
        for (Couple<RangeMarker> couple : myMarkers) {
          final RangeMarker leader = couple.first;
          final RangeMarker support = couple.second;
          final String name = document.getText(new TextRange(leader.getStartOffset(), leader.getEndOffset()));
          if (!name.equals(document.getText(new TextRange(support.getStartOffset(), support.getEndOffset())))) {
            document.replaceString(support.getStartOffset(), support.getEndOffset(), name);
          }
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

      myState = State.TRACKING;
    }

    private RangeMarker findSupport(RangeMarker leader, PsiFile file, Document document) {
      final int offset = leader.getStartOffset();
      PsiElement element = InjectedLanguageUtil.findElementAtNoCommit(file, offset);
      PsiElement support = findSupportElement(element);
      if (support == null && file.getViewProvider() instanceof MultiplePsiFilesPerDocumentFileViewProvider) {
        element = file.getViewProvider().findElementAt(offset, myLanguage);
        support = findSupportElement(element);
      }

      if (support == null) return null;

      final TextRange range = support.getTextRange();
      TextRange realRange = InjectedLanguageManager.getInstance(file.getProject()).injectedToHost(element.getContainingFile(), range);
      return document.createRangeMarker(realRange.getStartOffset(), realRange.getEndOffset(), true);
    }

    private static PsiElement findSupportElement(PsiElement element) {
      if (element == null || TreeUtil.findSibling(element.getNode(), XmlTokenType.XML_TAG_END) == null) return null;
      PsiElement support = RenameTagBeginOrEndIntentionAction.findOtherSide(element, false);
      support = support == null || element == support ? RenameTagBeginOrEndIntentionAction.findOtherSide(element, true) : support;
      return support != null && StringUtil.equals(element.getText(), support.getText()) ? support : null;
    }
  }
}
