/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xml.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomChangeSet;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * @author spleaner
 */
public class BreadcrumbsXmlWrapper implements BreadcrumbsItemListener<BreadcrumbsPsiItem>, Disposable {
  private final BreadcrumbsComponent<BreadcrumbsPsiItem> myComponent;
  private final Project myProject;
  private Editor myEditor;
  private final VirtualFile myFile;
  private boolean myUserCaretChange;
  private final MergingUpdateQueue myQueue;
  private final BreadcrumbsInfoProvider myInfoProvider;
  private final JPanel myWrapperPanel;

  public BreadcrumbsXmlWrapper(@NotNull final Editor editor) {
    myEditor = editor;

    final Project project = editor.getProject();
    assert project != null;
    myProject = project;

    Document document = myEditor.getDocument();
    myFile = FileDocumentManager.getInstance().getFile(document);


    final FileStatusManager manager = FileStatusManager.getInstance(project);
    manager.addFileStatusListener(new FileStatusListener() {
      public void fileStatusesChanged() {
        if (myComponent != null) {
          final Font editorFont = myEditor.getColorsScheme().getFont(EditorFontType.PLAIN);
          myComponent.setFont(editorFont.deriveFont(Font.PLAIN, editorFont.getSize2D()));
          updateCrumbs(myEditor.getCaretModel().getLogicalPosition());
        }
      }

      public void fileStatusChanged(@NotNull final VirtualFile virtualFile) {
      }
    }, this);


    myInfoProvider = findInfoProvider(findViewProvider());

    final CaretListener caretListener = new CaretListener() {
      public void caretPositionChanged(final CaretEvent e) {
        if (myUserCaretChange) {
          myQueue.cancelAllUpdates();
          myQueue.queue(new MyUpdate(BreadcrumbsXmlWrapper.this, editor));
        }

        myUserCaretChange = true;
      }
    };

    editor.getCaretModel().addCaretListener(caretListener);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        editor.getCaretModel().removeCaretListener(caretListener);
      }
    });

    PomManager.getModel(project).addModelListener(new PomModelListener() {
      public void modelChanged(final PomModelEvent event) {
        final PomChangeSet set = event.getChangeSet(event.getSource().getModelAspect(XmlAspect.class));
        if (set instanceof XmlChangeSet && myQueue != null) {
          myQueue.cancelAllUpdates();
          myQueue.queue(new MyUpdate(BreadcrumbsXmlWrapper.this, editor));
        }
      }

      public boolean isAspectChangeInteresting(final PomModelAspect aspect) {
        return aspect instanceof XmlAspect;
      }
    }, this);

    myComponent = new BreadcrumbsComponent<BreadcrumbsPsiItem>();
    myComponent.addBreadcrumbsItemListener(this);

    final Font editorFont = editor.getColorsScheme().getFont(EditorFontType.PLAIN);
    myComponent.setFont(editorFont.deriveFont(Font.PLAIN, editorFont.getSize2D()));

    final ComponentAdapter resizeListener = new ComponentAdapter() {
      public void componentResized(final ComponentEvent e) {
        myQueue.cancelAllUpdates();
        myQueue.queue(new MyUpdate(BreadcrumbsXmlWrapper.this, editor));
      }
    };

    myComponent.addComponentListener(resizeListener);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        myComponent.removeComponentListener(resizeListener);
      }
    });

    myQueue = new MergingUpdateQueue("Breadcrumbs.Queue", 200, true, myComponent);
    myQueue.queue(new MyUpdate(this, editor));

    Disposer.register(this, new UiNotifyConnector(myComponent, myQueue));
    Disposer.register(this, myQueue);

    myWrapperPanel = new JPanel();
    myWrapperPanel.setLayout(new BorderLayout());
    myWrapperPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 1, 2));
    myWrapperPanel.setOpaque(false);

    myWrapperPanel.add(myComponent, BorderLayout.CENTER);
  }

  private void moveEditorCaretTo(@NotNull final PsiElement element) {
    if (element.isValid()) {
      setUserCaretChange(false);
      myEditor.getCaretModel().moveToOffset(element.getTextOffset());
      myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
  }

  @Nullable   
  private BreadcrumbsInfoProvider findProviderForElement(@NotNull final PsiElement element) {
    final BreadcrumbsInfoProvider provider = getInfoProvider(element.getLanguage());
    return provider == null ? myInfoProvider : provider;
  }

  private void setUserCaretChange(final boolean userCaretChange) {
    myUserCaretChange = userCaretChange;
  }

  @Nullable
  private List<BreadcrumbsPsiItem> getLineElements(@NotNull final LogicalPosition position) {
    PsiElement element = findFirstBreadcrumbedElement(position);
    if (element == null) return null;

    final LinkedList<BreadcrumbsPsiItem> result = new LinkedList<BreadcrumbsPsiItem>();
    while (element != null) {
      BreadcrumbsInfoProvider provider = findProviderForElement(element);

      if (provider != null && provider.acceptElement(element)) {
        result.addFirst(new BreadcrumbsPsiItem(element, provider));
      }

      element = (provider != null) ? provider.getParent(element) : element.getParent();
    }

    return result;
  }

  @Nullable
  private PsiElement findFirstBreadcrumbedElement(final LogicalPosition position) {
    if (myFile == null || !myFile.isValid()) return null;

    final int offset = myEditor.logicalPositionToOffset(position);
    PriorityQueue<PsiElement> leafs = new PriorityQueue<PsiElement>(3, new Comparator<PsiElement>() {
      public int compare(final PsiElement o1, final PsiElement o2) {
        return o2.getTextRange().getStartOffset() - o1.getTextRange().getStartOffset();
      }
    });
    FileViewProvider viewProvider = findViewProvider();
    if (viewProvider == null) return null;

    for (final Language language : viewProvider.getLanguages()) {
      ContainerUtil.addIfNotNull(viewProvider.findElementAt(offset, language), leafs);
    }
    while (!leafs.isEmpty()) {
      final PsiElement element = leafs.remove();
      if (!element.isValid()) continue;

      BreadcrumbsInfoProvider provider = findProviderForElement(element);
      if (provider != null && provider.acceptElement(element)) {
        return element;
      }
      if (!(element instanceof PsiFile)) {
        ContainerUtil.addIfNotNull(element.getParent(), leafs);
      }
    }
    return null;
  }

  @Nullable
  private FileViewProvider findViewProvider() {
    if (myFile == null) return null;
    return PsiManager.getInstance(myProject).findViewProvider(myFile);
  }

  private void updateCrumbs(final LogicalPosition position) {
    if (myFile != null && myEditor != null) {
      if (PsiDocumentManager.getInstance(myProject).isUncommited(myEditor.getDocument())) {
        return;
      }

      myComponent.setItems(getLineElements(position));
    }
  }

  @Nullable
  static BreadcrumbsInfoProvider findInfoProvider(@Nullable FileViewProvider viewProvider) {
    BreadcrumbsInfoProvider provider = null;
    if (viewProvider != null) {
      final Language baseLang = viewProvider.getBaseLanguage();
      provider = getInfoProvider(baseLang);
      if (provider == null) {
        for (final Language language : viewProvider.getLanguages()) {
          provider = getInfoProvider(language);
          if (provider != null) {
            break;
          }
        }
      }
    }
    return provider;
  }

  public JComponent getComponent() {
    return myWrapperPanel;
  }

  public void itemSelected(@NotNull final BreadcrumbsPsiItem item, final int modifiers) {
    final PsiElement psiElement = item.getPsiElement();
    moveEditorCaretTo(psiElement);

    if ((modifiers & Event.SHIFT_MASK) == Event.SHIFT_MASK || (modifiers & Event.META_MASK) == Event.META_MASK) {
      final TextRange range = psiElement.getTextRange();
      myEditor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    }
  }

  public void dispose() {
    myEditor = null;
  }

  @Nullable
  private static BreadcrumbsInfoProvider getInfoProvider(@NotNull final Language language) {
    for (final BreadcrumbsInfoProvider provider : Extensions.getExtensions(BreadcrumbsInfoProvider.EP_NAME)) {
      for (final Language language1 : provider.getLanguages()) {
        if (language.isKindOf(language1)) {
          return provider;
        }
      }
    }

    return null;
  }

  private class MyUpdate extends Update {
    private final BreadcrumbsXmlWrapper myBreadcrumbsComponent;
    private final Editor myEditor;

    public MyUpdate(@NonNls final BreadcrumbsXmlWrapper c, @NotNull final Editor editor) {
      super(c);

      myBreadcrumbsComponent = c;
      myEditor = editor;
    }

    public void run() {
      myBreadcrumbsComponent.updateCrumbs(myEditor.getCaretModel().getLogicalPosition());
    }

    public boolean canEat(final Update update) {
      return true;
    }
  }

}
