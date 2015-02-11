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
package com.intellij.util.xml.ui;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.*;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.events.DomEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
public class DomFileEditor<T extends BasicDomElementComponent> extends PerspectiveFileEditor implements CommittablePanel, Highlightable {
  private final String myName;
  private final Factory<? extends T> myComponentFactory;
  private T myComponent;

  public DomFileEditor(final DomElement element, final String name, final T component) {
    this(element.getManager().getProject(), DomUtil.getFile(element).getVirtualFile(), name, component);
  }

  public DomFileEditor(final Project project, final VirtualFile file, final String name, final T component) {
    this(project, file, name, new Factory<T>() {
      @Override
      public T create() {
        return component;
      }
    });
  }

  public DomFileEditor(final Project project, final VirtualFile file, final String name, final Factory<? extends T> component) {
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
        ServiceManager.getService(getProject(), CommittableUtil.class).commit(myComponent);
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
      public void eventOccured(DomEvent event) {
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

  public static DomFileEditor createDomFileEditor(final String name,
                                                  @Nullable final Icon icon,
                                                  final DomElement element,
                                                  final Factory<? extends CommittablePanel> committablePanel) {

    final XmlFile file = DomUtil.getFile(element);
    final Factory<BasicDomElementComponent> factory = new Factory<BasicDomElementComponent>() {
      @Override
      public BasicDomElementComponent create() {

        CaptionComponent captionComponent = new CaptionComponent(name, icon);
        captionComponent.initErrorPanel(element);
        BasicDomElementComponent component = createComponentWithCaption(committablePanel.create(), captionComponent, element);
        Disposer.register(component, captionComponent);
        return component;
      }
    };
    return new DomFileEditor<BasicDomElementComponent>(file.getProject(), file.getVirtualFile(), name, factory) {
      @Override
      public JComponent getPreferredFocusedComponent() {
        return null;
      }
    };
  }

  public static BasicDomElementComponent createComponentWithCaption(final CommittablePanel committablePanel,
                                                                    final CaptionComponent captionComponent,
                                                                    final DomElement element) {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(captionComponent, BorderLayout.NORTH);
    panel.add(element.isValid() ? committablePanel.getComponent() : new JPanel(), BorderLayout.CENTER);

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
