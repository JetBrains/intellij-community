// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.split;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBSplitter;
import com.intellij.util.ui.JBEmptyBorder;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated Use {@link TextEditorWithPreview}
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
public abstract class SplitFileEditor<E1 extends FileEditor, E2 extends FileEditor> extends UserDataHolderBase implements FileEditor {
  public static final Key<SplitFileEditor> PARENT_SPLIT_KEY = Key.create("parentSplit");

  private static final String MY_PROPORTION_KEY = "SplitFileEditor.Proportion";

  @NotNull
  protected final E1 myMainEditor;
  @NotNull
  protected final E2 mySecondEditor;
  @NotNull
  private final JComponent myComponent;
  @NotNull
  private TextEditorWithPreview.Layout mySplitEditorLayout =
    MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().getSplitEditorLayout();

  private boolean myVerticalSplitOption = MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isVerticalSplit();
  @NotNull
  private final MyListenersMultimap myListenersGenerator = new MyListenersMultimap();

  private SplitEditorToolbar myToolbarWrapper;
  private JBSplitter mySplitter;

  public SplitFileEditor(@NotNull E1 mainEditor, @NotNull E2 secondEditor) {
    myMainEditor = mainEditor;
    mySecondEditor = secondEditor;

    adjustDefaultLayout(mainEditor);
    myComponent = createComponent();

    if (myMainEditor instanceof TextEditor) {
      myMainEditor.putUserData(PARENT_SPLIT_KEY, this);
    }
    if (mySecondEditor instanceof TextEditor) {
      mySecondEditor.putUserData(PARENT_SPLIT_KEY, this);
    }

    MarkdownApplicationSettings.SettingsChangedListener settingsChangedListener =
      new MarkdownApplicationSettings.SettingsChangedListener() {
        @Override
        public void beforeSettingsChanged(@NotNull MarkdownApplicationSettings newSettings) {
          TextEditorWithPreview.Layout oldSplitEditorLayout =
            MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().getSplitEditorLayout();

          boolean oldVerticalSplitOption =
            MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isVerticalSplit();

          ApplicationManager.getApplication().invokeLater(() -> {
            if (oldSplitEditorLayout == mySplitEditorLayout) {
              triggerLayoutChange(newSettings.getMarkdownPreviewSettings().getSplitEditorLayout(), false);
            }

            if (oldVerticalSplitOption == myVerticalSplitOption) {
              triggerSplitOrientationChange(newSettings.getMarkdownPreviewSettings().isVerticalSplit());
            }
          });
        }
      };

    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(MarkdownApplicationSettings.SettingsChangedListener.TOPIC, settingsChangedListener);
  }

  private void adjustDefaultLayout(E1 editor) {
  }

  private void triggerSplitOrientationChange(boolean isVerticalSplit) {
    if (myVerticalSplitOption == isVerticalSplit) {
      return;
    }

    myVerticalSplitOption = isVerticalSplit;

    myToolbarWrapper.refresh();
    mySplitter.setOrientation(!myVerticalSplitOption);
    myComponent.repaint();
  }

  @NotNull
  private JComponent createComponent() {
    mySplitter =
      new JBSplitter(!MarkdownApplicationSettings.getInstance().getMarkdownPreviewSettings().isVerticalSplit(), 0.5f, 0.15f, 0.85f);
    mySplitter.setSplitterProportionKey(MY_PROPORTION_KEY);
    mySplitter.setFirstComponent(myMainEditor.getComponent());
    mySplitter.setSecondComponent(mySecondEditor.getComponent());
    mySplitter.setDividerWidth(3);

    myToolbarWrapper = createMarkdownToolbarWrapper(mySplitter);

    final JPanel result = new JPanel(new BorderLayout());
    result.add(myToolbarWrapper, BorderLayout.NORTH);
    result.add(mySplitter, BorderLayout.CENTER);
    adjustEditorsVisibility();

    return result;
  }

  @NotNull
  private static SplitEditorToolbar createMarkdownToolbarWrapper(@NotNull JComponent targetComponentForActions) {
    ActionToolbar leftToolbar = createToolbarFromGroupId("Markdown.Toolbar.Left");
    leftToolbar.setTargetComponent(targetComponentForActions);
    leftToolbar.setReservePlaceAutoPopupIcon(false);

    ActionToolbar rightToolbar = createToolbarFromGroupId("Markdown.Toolbar.Right");
    rightToolbar.setTargetComponent(targetComponentForActions);
    rightToolbar.setReservePlaceAutoPopupIcon(false);

    return new SplitEditorToolbar(leftToolbar, rightToolbar);
  }

  @NotNull
  private static ActionToolbar createToolbarFromGroupId(@NotNull String groupId) {
    final ActionManager actionManager = ActionManager.getInstance();

    if (!actionManager.isGroup(groupId)) {
      throw new IllegalStateException(groupId + " should have been a group");
    }
    final ActionGroup group = ((ActionGroup)actionManager.getAction(groupId));
    final ActionToolbarImpl editorToolbar =
      ((ActionToolbarImpl)actionManager.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, group, true));
    editorToolbar.setBorder(new JBEmptyBorder(0, 2, 0, 2));

    return editorToolbar;
  }

  public void triggerLayoutChange(@NotNull TextEditorWithPreview.Layout newLayout, boolean requestFocus) {
    if (mySplitEditorLayout == newLayout) {
      return;
    }

    mySplitEditorLayout = newLayout;
    invalidateLayout(requestFocus);
  }

  private void invalidateLayout(boolean requestFocus) {
    adjustEditorsVisibility();
    myToolbarWrapper.refresh();
    myComponent.repaint();

    if (!requestFocus) return;

    final JComponent focusComponent = getPreferredFocusedComponent();
    if (focusComponent != null) {
      IdeFocusManager.findInstanceByComponent(focusComponent).requestFocus(focusComponent, true);
    }
  }

  private void adjustEditorsVisibility() {
  }

  @NotNull
  public E1 getMainEditor() {
    return myMainEditor;
  }

  @NotNull
  public E2 getSecondEditor() {
    return mySecondEditor;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return new MyFileEditorState(mySplitEditorLayout.name(), myMainEditor.getState(level), mySecondEditor.getState(level));
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return myMainEditor.isModified() || mySecondEditor.isModified();
  }

  @Override
  public boolean isValid() {
    return myMainEditor.isValid() && mySecondEditor.isValid();
  }

  @Override
  public void selectNotify() {
    myMainEditor.selectNotify();
    mySecondEditor.selectNotify();
  }

  @Override
  public void deselectNotify() {
    myMainEditor.deselectNotify();
    mySecondEditor.deselectNotify();
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myMainEditor.addPropertyChangeListener(listener);
    mySecondEditor.addPropertyChangeListener(listener);

    final DoublingEventListenerDelegate delegate = myListenersGenerator.addListenerAndGetDelegate(listener);
    myMainEditor.addPropertyChangeListener(delegate);
    mySecondEditor.addPropertyChangeListener(delegate);
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myMainEditor.removePropertyChangeListener(listener);
    mySecondEditor.removePropertyChangeListener(listener);

    final DoublingEventListenerDelegate delegate = myListenersGenerator.removeListenerAndGetDelegate(listener);
    if (delegate != null) {
      myMainEditor.removePropertyChangeListener(delegate);
      mySecondEditor.removePropertyChangeListener(delegate);
    }
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return myMainEditor.getBackgroundHighlighter();
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return myMainEditor.getCurrentLocation();
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return myMainEditor.getStructureViewBuilder();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myMainEditor);
    Disposer.dispose(mySecondEditor);
  }

  public static class MyFileEditorState implements FileEditorState {
    @Nullable
    private final String mySplitLayout;
    @Nullable
    private final FileEditorState myFirstState;
    @Nullable
    private final FileEditorState mySecondState;

    public MyFileEditorState(@Nullable String splitLayout, @Nullable FileEditorState firstState, @Nullable FileEditorState secondState) {
      mySplitLayout = splitLayout;
      myFirstState = firstState;
      mySecondState = secondState;
    }

    @Nullable
    public String getSplitLayout() {
      return mySplitLayout;
    }

    @Nullable
    public FileEditorState getFirstState() {
      return myFirstState;
    }

    @Nullable
    public FileEditorState getSecondState() {
      return mySecondState;
    }

    @Override
    public boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level) {
      return otherState instanceof MyFileEditorState
             && (myFirstState == null || myFirstState.canBeMergedWith(((MyFileEditorState)otherState).myFirstState, level))
             && (mySecondState == null || mySecondState.canBeMergedWith(((MyFileEditorState)otherState).mySecondState, level));
    }
  }

  private final class DoublingEventListenerDelegate implements PropertyChangeListener {
    @NotNull
    private final PropertyChangeListener myDelegate;

    private DoublingEventListenerDelegate(@NotNull PropertyChangeListener delegate) {
      myDelegate = delegate;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      myDelegate.propertyChange(new PropertyChangeEvent(SplitFileEditor.this, evt.getPropertyName(), evt.getOldValue(), evt.getNewValue()));
    }
  }

  private class MyListenersMultimap {
    private final Map<PropertyChangeListener, Pair<Integer, DoublingEventListenerDelegate>> myMap = new HashMap<>();

    @NotNull
    public DoublingEventListenerDelegate addListenerAndGetDelegate(@NotNull PropertyChangeListener listener) {
      if (!myMap.containsKey(listener)) {
        myMap.put(listener, Pair.create(1, new DoublingEventListenerDelegate(listener)));
      }
      else {
        final Pair<Integer, DoublingEventListenerDelegate> oldPair = myMap.get(listener);
        myMap.put(listener, Pair.create(oldPair.getFirst() + 1, oldPair.getSecond()));
      }

      return myMap.get(listener).getSecond();
    }

    @Nullable
    public DoublingEventListenerDelegate removeListenerAndGetDelegate(@NotNull PropertyChangeListener listener) {
      final Pair<Integer, DoublingEventListenerDelegate> oldPair = myMap.get(listener);
      if (oldPair == null) {
        return null;
      }

      if (oldPair.getFirst() == 1) {
        myMap.remove(listener);
      }
      else {
        myMap.put(listener, Pair.create(oldPair.getFirst() - 1, oldPair.getSecond()));
      }
      return oldPair.getSecond();
    }
  }

  /**
   * @deprecated Use {@link TextEditorWithPreview.Layout}
   */
  @Deprecated
  public enum SplitEditorLayout {
    FIRST(true, false, MarkdownBundle.message("markdown.layout.editor.only")),
    SECOND(false, true, MarkdownBundle.message("markdown.layout.preview.only")),
    SPLIT(true, true, MarkdownBundle.message("markdown.layout.editor.and.preview"));

    public final boolean showFirst;
    public final boolean showSecond;
    public final String presentationName;

    SplitEditorLayout(boolean showFirst, boolean showSecond, String presentationName) {
      this.showFirst = showFirst;
      this.showSecond = showSecond;
      this.presentationName = presentationName;
    }

    public String getPresentationText() {
      return StringUtil.capitalize(presentationName);
    }

    @Override
    public String toString() {
      return MarkdownBundle.message("markdown.layout.show", presentationName);
    }
  }
}
