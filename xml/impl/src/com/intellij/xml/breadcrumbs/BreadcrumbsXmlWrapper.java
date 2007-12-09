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
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
import java.util.LinkedList;
import java.util.List;

/**
 * @author spleaner
 */
public class BreadcrumbsXmlWrapper implements BreadcrumbsItemListener<BreadcrumbsPsiItem>, Disposable {
  private BreadcrumbsComponent<BreadcrumbsPsiItem> myComponent;
  private Editor myEditor;
  private BreadcrumbsLoaderComponentImpl myLoaderComponent;
  private PsiFile myFile;
  private boolean myUserCaretChange;
  private MergingUpdateQueue myQueue;
  private BreadcrumbsInfoProvider myInfoProvider;

  public BreadcrumbsXmlWrapper(@NotNull final Editor editor, @NotNull final BreadcrumbsLoaderComponentImpl loaderComponent) {
    myEditor = editor;
    myLoaderComponent = loaderComponent;

    final Project project = editor.getProject();
    assert project != null;

    Document document = myEditor.getDocument();
    myFile = PsiDocumentManager.getInstance(project).getPsiFile(document);

    if (myFile != null) {
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
    }

    myInfoProvider = findInfoProvider(myFile, loaderComponent);

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
  }

  private void moveEditorCaretTo(@NotNull final PsiElement element) {
    if (element.isValid()) {
      setUserCaretChange(false);
      myEditor.getCaretModel().moveToOffset(element.getTextOffset());
      myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
  }

  @Nullable
  BreadcrumbsInfoProvider findProviderForElement(@NotNull final PsiElement element) {
    return myLoaderComponent.getInfoProvider(element.getLanguage());
  }

  private void setUserCaretChange(final boolean userCaretChange) {
    myUserCaretChange = userCaretChange;
  }

  @Nullable
  private PsiElement getCaretElement(@NotNull final LogicalPosition position) {
    if (myFile == null) {
      return null;
    }

    final int offset = myEditor.logicalPositionToOffset(position);

    //final PsiFile file = myFile.getViewProvider().getPsi(myLanguage);
    //return file != null ? file.getViewProvider().findElementAt(offset) : null;
    return myFile.isValid() ? myFile.getViewProvider().findElementAt(offset) : null;
  }

  @Nullable
  private List<BreadcrumbsPsiItem> getLineElements(@Nullable final PsiElement endElement) {
    if (endElement == null || !endElement.isValid()) {
      return null;
    }

    final LinkedList<BreadcrumbsPsiItem> result = new LinkedList<BreadcrumbsPsiItem>();

    PsiElement element = endElement;
    while (element != null) {
      BreadcrumbsInfoProvider provider = findProviderForElement(element);
      if (provider == null) {
        provider = myInfoProvider;
      }

      if (provider != null && provider.acceptElement(element)) {
        result.addFirst(new BreadcrumbsPsiItem(element, provider));
      }

      element = (provider != null) ? provider.getParent(element) : element.getParent();
    }

    return result;
  }

  private void updateCrumbs(final LogicalPosition position) {
    if (myFile != null && myEditor != null) {
      if (PsiDocumentManager.getInstance(myFile.getProject()).isUncommited(myEditor.getDocument())) {
        return;
      }

      myComponent.setItems(getLineElements(getCaretElement(position)));
    }
  }

  @Nullable
  private static BreadcrumbsInfoProvider findInfoProvider(@Nullable final PsiFile file,
                                                          @NotNull BreadcrumbsLoaderComponentImpl loaderComponent) {
    BreadcrumbsInfoProvider provider = null;
    if (file != null) {
      final FileViewProvider viewProvider = file.getViewProvider();
      final Language baseLang = viewProvider.getBaseLanguage();
      provider = loaderComponent.getInfoProvider(baseLang);
      if (provider == null) {
        for (final Language language : viewProvider.getPrimaryLanguages()) {
          provider = loaderComponent.getInfoProvider(language);
          if (provider != null) {
            break;
          }
        }
      }
    }

    return provider;
  }

  public JComponent getComponent() {
    return myComponent;
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
    myLoaderComponent = null;
  }

  private class MyUpdate extends Update {
    private BreadcrumbsXmlWrapper myBreadcrumbsComponent;
    private Editor myEditor;

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
