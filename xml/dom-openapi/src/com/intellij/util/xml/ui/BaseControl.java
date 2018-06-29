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
package com.intellij.util.xml.ui;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.EventDispatcher;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.lang.reflect.InvocationTargetException;

/**
 * @author peter
 */
public abstract class BaseControl<Bound extends JComponent, T> extends DomUIControl implements Highlightable {
  private static final Logger LOG = Logger.getInstance(BaseControl.class);
  public static final Color ERROR_BACKGROUND = new Color(255,204,204);
  public static final Color ERROR_FOREGROUND = SimpleTextAttributes.ERROR_ATTRIBUTES.getFgColor();
  public static final Color WARNING_BACKGROUND = new Color(255,255,204);

  private final EventDispatcher<CommitListener> myDispatcher = EventDispatcher.create(CommitListener.class);

  private Bound myBoundComponent;
  private final DomWrapper<T> myDomWrapper;
  private boolean myCommitting;

  private Color myDefaultForeground;
  private Color myDefaultBackground;

  protected BaseControl(final DomWrapper<T> domWrapper) {
    myDomWrapper = domWrapper;
  }

  private void checkInitialized() {
    if (myBoundComponent != null) return;

    initialize(null);
  }

  protected JComponent getHighlightedComponent(Bound component) {
    return component;
  }

  protected final Color getDefaultBackground() {
    return myDefaultBackground;
  }

  protected final Color getDefaultForeground() {
    return myDefaultForeground;
  }

  protected final Color getErrorBackground() {
    return ERROR_BACKGROUND;
  }

  protected final Color getWarningBackground() {
    return WARNING_BACKGROUND;
  }

  protected final Color getErrorForeground() {
    return ERROR_FOREGROUND;
  }


  private void initialize(final Bound boundComponent) {
    myBoundComponent = createMainComponent(boundComponent);
    final JComponent highlightedComponent = getHighlightedComponent(myBoundComponent);
    myDefaultForeground = highlightedComponent.getForeground();
    myDefaultBackground = highlightedComponent.getBackground();
    final JComponent component = getComponentToListenFocusLost(myBoundComponent);
    if (component != null) {
      component.addFocusListener(new FocusListener() {
        @Override
        public void focusGained(FocusEvent e) {
        }

        @Override
        public void focusLost(FocusEvent e) {
          if (!e.isTemporary() && isValid()) {
            commit();
          }
        }
      });
    }

    updateComponent();
  }

  @Nullable
  protected JComponent getComponentToListenFocusLost(Bound component) {
    return null;
  }

  protected abstract Bound createMainComponent(Bound boundedComponent);

  @Override
  public void bind(JComponent component) {
    initialize((Bound)component);
  }

  @Override
  public void addCommitListener(CommitListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void removeCommitListener(CommitListener listener) {
    myDispatcher.removeListener(listener);
  }

  @Override
  public final DomElement getDomElement() {
    return myDomWrapper.getWrappedElement();
  }

  public final DomWrapper<T> getDomWrapper() {
    return myDomWrapper;
  }

  @Override
  public final Bound getComponent() {
    checkInitialized();
    return myBoundComponent;
  }

  @Override
  public void dispose() {
  }

  @Override
  public final void commit() {
    if (isValid() && !isCommitted()) {
      setValueToXml(getValue());
      updateComponent();
    }
  }

  protected final boolean isValid() {
    return myDomWrapper.isValid();
  }

  private static boolean valuesAreEqual(final Object valueInXml, final Object valueInControl) {
    return "".equals(valueInControl) && null == valueInXml ||
           equalModuloTrim(valueInXml, valueInControl) ||
           Comparing.equal(valueInXml, valueInControl);
  }

  private static boolean equalModuloTrim(final Object valueInXml, final Object valueInControl) {
    return valueInXml instanceof String && valueInControl instanceof String && ((String)valueInXml).trim().equals(((String)valueInControl).trim());
  }

  @Override
  public final void reset() {
    if (!myCommitting) {
      doReset();
      updateComponent();
    }
  }

  @Override
  public void updateHighlighting() {
    updateComponent();
  }

  protected void updateComponent() {
  }

  protected void doReset() {
    if (valuesDiffer()) {
      setValue(getValueFromXml());
    }
  }

  protected boolean isCommitted() {
    return !valuesDiffer();
  }

  private boolean valuesDiffer() {
    return !valuesAreEqual(getValueFromXml(), getValue());
  }

  private void setValueToXml(final T value) {
    if (myCommitting) return;
    myCommitting = true;
    try {
      final CommitListener multicaster = myDispatcher.getMulticaster();
      multicaster.beforeCommit(this);
      try {
        WriteCommandAction.writeCommandAction(getProject(), getDomWrapper().getFile()).run(() -> {
          doCommit(value);
        });
      }
      catch (ReflectiveOperationException e) {
        LOG.error(e);
      }
      multicaster.afterCommit(this);
    }
    finally {
      myCommitting = false;
    }
  }

  protected void doCommit(final T value) throws IllegalAccessException, InvocationTargetException {
    myDomWrapper.setValue("".equals(value) ? null : value);
  }

  protected final Project getProject() {
    return myDomWrapper.getProject();
  }

  private T getValueFromXml() {
    try {
      return myDomWrapper.getValue();
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }


  @Override
  public boolean canNavigate(DomElement element) {
    return false;
  }

  @Override
  public void navigate(DomElement element) {
  }

  @Nullable
  protected abstract T getValue();
  protected abstract void setValue(T value);

}
