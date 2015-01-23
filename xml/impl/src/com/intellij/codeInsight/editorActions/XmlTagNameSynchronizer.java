/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInspection.htmlInspections.RenameTagBeginOrEndIntentionAction;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class XmlTagNameSynchronizer extends CommandAdapter implements ApplicationComponent {
  private static final Key<TagNameSynchronizer> SYNCHRONIZER_KEY = Key.create("tag_name_synchronizer");
  private final FileDocumentManager myFileDocumentManager;

  public XmlTagNameSynchronizer(EditorFactory editorFactory, FileDocumentManager manager, CommandProcessor processor) {
    myFileDocumentManager = manager;
    editorFactory.addEditorFactoryListener(new EditorFactoryAdapter() {
      @Override
      public void editorCreated(@NotNull EditorFactoryEvent event) {
        installSynchronizer(event.getEditor());
      }

      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        uninstallSynchronizer(event);
      }
    }, ApplicationManager.getApplication());
    processor.addCommandListener(this);
  }

  public void uninstallSynchronizer(@NotNull EditorFactoryEvent event) {
    final Document document = event.getEditor().getDocument();
    final TagNameSynchronizer synchronizer = findSynchronizer(document);
    if (synchronizer != null) {
      synchronizer.clearMarkers();
    }
    document.putUserData(SYNCHRONIZER_KEY, null);
  }

  private void installSynchronizer(Editor editor) {
    final Project project = editor.getProject();
    if (project == null) return;

    final Document document = editor.getDocument();
    final VirtualFile file = myFileDocumentManager.getFile(document);
    final FileType type = file != null ? file.getFileType() : null;
    if (isAccepted(type)) new TagNameSynchronizer(editor, project);
  }

  private static boolean isAccepted(FileType type) {
    return type == XmlFileType.INSTANCE || type == HtmlFileType.INSTANCE || type == XHtmlFileType.INSTANCE;
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "XmlTagNameSynchronizer";
  }

  @Override
  public void initComponent() {

  }

  @Override
  public void disposeComponent() {

  }

  @Nullable
  public TagNameSynchronizer findSynchronizer(final Document document) {
    if (!WebEditorOptions.getInstance().isSyncTagEditing() || document == null) return null;
    return document.getUserData(SYNCHRONIZER_KEY);
  }

  @Override
  public void beforeCommandFinished(CommandEvent event) {
    final TagNameSynchronizer synchronizer = findSynchronizer(event.getDocument());
    if (synchronizer != null) {
      synchronizer.beforeCommandFinished();
    }
  }

  private static class TagNameSynchronizer extends DocumentAdapter {
    private PsiDocumentManager myDocumentManager;

    private enum State {INITIAL, TRACKING, APPLYING}

    private final Editor myEditor;
    private State myState = State.INITIAL;
    private final List<Couple<RangeMarker>> myMarkers = new SmartList<Couple<RangeMarker>>();

    public TagNameSynchronizer(Editor editor, Project project) {
      myEditor = editor;
      final Disposable disposable = ((EditorImpl)editor).getDisposable();
      final Document document = editor.getDocument();
      document.addDocumentListener(this, disposable);
      document.putUserData(SYNCHRONIZER_KEY, this);
      myDocumentManager = PsiDocumentManager.getInstance(project);
    }

    @Override
    public void beforeDocumentChange(DocumentEvent event) {
      if (!WebEditorOptions.getInstance().isSyncTagEditing()) return;

      if (myState == State.APPLYING) return;

      final Document document = event.getDocument();
      if (myState == State.INITIAL) {
        final PsiFile file = myDocumentManager.getPsiFile(document);
        if (file == null) return;

        final SmartList<RangeMarker> leaders = new SmartList<RangeMarker>();
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

        myState = State.TRACKING;
      }
      if (myMarkers.isEmpty()) return;

      final CharSequence fragment = event.getNewFragment();
      final int offset = event.getOffset();
      final int newLength = event.getNewLength();
      final int oldLength = event.getOldLength();

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

      boolean fitsInMarker = false;
      for (Couple<RangeMarker> leaderAndSupport : myMarkers) {
        final RangeMarker leader = leaderAndSupport.first;
        if (!leader.isValid()) {
          fitsInMarker = false;
          break;
        }
        fitsInMarker |= offset >= leader.getStartOffset() && offset + oldLength <= leader.getEndOffset();
      }
      if (!fitsInMarker) {
        clearMarkers();
        beforeDocumentChange(event);
      }
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
      for (int i = offset - 1; i >= Math.max(0, offset - 50); i--) {
        final char c = sequence.charAt(i);
        if (c == '<' || (c == '/' && i > 0 && sequence.charAt(i - 1) == '<')) {
          start = i + 1;
          break;
        }
        if (!XmlUtil.isValidTagNameChar(c)) break;
      }
      if (start < 0) return null;
      for (int i = offset; i < Math.min(document.getTextLength(), offset + 50); i++) {
        final char c = sequence.charAt(i);
        if (!XmlUtil.isValidTagNameChar(c)) {
          end = i;
          break;
        }
      }
      if (end < 0 || start >= end) return null;
      return document.createRangeMarker(start, end, true);
    }

    public void beforeCommandFinished() {
      if (myMarkers.isEmpty()) return;

      myState = State.APPLYING;

      final Document document = myEditor.getDocument();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          for (Couple<RangeMarker> couple : myMarkers) {
            final RangeMarker leader = couple.first;
            final RangeMarker support = couple.second;
            final String name = document.getText(new TextRange(leader.getStartOffset(), leader.getEndOffset()));
            document.replaceString(support.getStartOffset(), support.getEndOffset(), name);
          }
        }
      });

      myState = State.TRACKING;
    }

    private static RangeMarker findSupport(RangeMarker leader, PsiFile file, Document document) {
      final PsiElement element = file.findElementAt(leader.getStartOffset());
      PsiElement support = RenameTagBeginOrEndIntentionAction.findOtherSide(element, false);
      support = support == null || element == support ? RenameTagBeginOrEndIntentionAction.findOtherSide(element, true) : support;
      final TextRange range = support != null ? support.getTextRange() : null;
      return range != null ? document.createRangeMarker(range.getStartOffset(), range.getEndOffset(), true) : null;
    }
  }
}
