// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.ui;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.DomEvent;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class DomFileEditor<T extends BasicDomElementComponent> extends PerspectiveFileEditor implements CommittablePanel, Highlightable {
  private final @Nls String myName;
  private final Factory<? extends T> myComponentFactory;
  private T myComponent;

  public DomFileEditor(final DomElement element, final @Nls String name, final T component) {
    this(element.getManager().getProject(), DomUtil.getFile(element).getVirtualFile(), name, component);
  }

  public DomFileEditor(final Project project, final VirtualFile file, final @Nls String name, final T component) {
    this(project, file, name, () -> component);
  }

  public DomFileEditor(final Project project, final VirtualFile file, final @Nls String name, final Factory<? extends T> component) {
    super(project, file);
    myComponentFactory = component;
    myName = name;

    DomElementAnnotationsManager.getInstance(project).addHighlightingListener(new DomElementAnnotationsManager.DomHighlightingListener() {
      @Override
      public void highlightingFinished(@NotNull DomFileElement element) {
        if (isInitialised() && getComponent().isShowing() && element.isValid()) {
          updateHighlighting();
        }
      }
    }, this);
  }

  @Override
  public void updateHighlighting() {
    if (checkIsValid()) {
      CommittableUtil.updateHighlighting(myComponent);
    }
  }

  @Override
  public void commit() {
    if (checkIsValid() && isInitialised()) {
      setShowing(false);
      try {
        getProject().getService(CommittableUtil.class).commit(myComponent);
      }
      finally {
        setShowing(true);
      }
    }
  }

  @Override
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    ensureInitialized();
    return myComponent.getComponent();
  }

  protected final T getDomComponent() {
    return myComponent;
  }

  @Override
  @NotNull
  protected JComponent createCustomComponent() {
    MnemonicHelper.init(getComponent());
    myComponent = myComponentFactory.create();
    DomUIFactory.getDomUIFactory().setupErrorOutdatingUserActivityWatcher(this, getDomElement());
    DomManager.getDomManager(getProject()).addDomEventListener(new DomEventListener() {
      @Override
      public void eventOccured(@NotNull DomEvent event) {
        checkIsValid();
      }
    }, this);
    Disposer.register(this, myComponent);
    return myComponent.getComponent();
  }

  @Override
  @NotNull
  public final String getName() {
    return myName;
  }

  @Override
  protected DomElement getSelectedDomElement() {
    if (myComponent == null) return null;
    return DomUINavigationProvider.findDomElement(myComponent);
  }

  @Override
  protected void setSelectedDomElement(DomElement domElement) {
    final DomUIControl domControl = DomUINavigationProvider.findDomControl(myComponent, domElement);
    if (domControl != null) {
      domControl.navigate(domElement);
    }
  }

  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    ensureInitialized();
    return DomUIFactory.getDomUIFactory().createDomHighlighter(getProject(), this, getDomElement());
  }

  private DomElement getDomElement() {
    return myComponent.getDomElement();
  }


  @Override
  public boolean isValid() {
    return super.isValid() && (!isInitialised() || getDomElement().isValid());
  }

  @Override
  public void reset() {
    if (checkIsValid()) {
      myComponent.reset();
    }
  }

  public static BasicDomElementComponent createComponentWithCaption(final CommittablePanel committablePanel,
                                                                    final CaptionComponent captionComponent,
                                                                    final @Nullable DomElement element) {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(captionComponent, BorderLayout.NORTH);
    panel.add(element != null && element.isValid() ? committablePanel.getComponent() : new JPanel(), BorderLayout.CENTER);

    BasicDomElementComponent component = new BasicDomElementComponent(element) {
      @Override
      public JComponent getComponent() {
        return panel;
      }
    };

    component.addComponent(committablePanel);
    component.addComponent(captionComponent);
    return component;
  }

}
