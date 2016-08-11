/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * User: Sergey.Vasiliev
 * Date: Nov 17, 2005
 */
public abstract class BasicDomElementComponent<T extends DomElement> extends AbstractDomElementComponent<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.ui.editors.BasicDomElementComponent");
  private final Map<JComponent, DomUIControl> myBoundComponents = new HashMap<>();

  public BasicDomElementComponent(T domElement) {
    super(domElement);
  }

  protected final void bindProperties() {
    bindProperties(getDomElement());
  }

  protected boolean commitOnEveryChange(GenericDomValue element) {
    return false;
  }

  protected final void bindProperties(final DomElement domElement) {
    if (domElement == null) return;

    DomElementAnnotationsManager.getInstance(domElement.getManager().getProject()).addHighlightingListener(new DomElementAnnotationsManager.DomHighlightingListener() {
      @Override
      public void highlightingFinished(@NotNull final DomFileElement element) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (getComponent().isShowing() && element.isValid()) {
            updateHighlighting();
          }
        });
      }
    }, this);

    for (final AbstractDomChildrenDescription description : domElement.getGenericInfo().getChildrenDescriptions()) {
      final JComponent boundComponent = getBoundComponent(description);
      if (boundComponent != null) {
        if (description instanceof DomFixedChildDescription && DomUtil.isGenericValueType(description.getType())) {
          if ((description.getValues(domElement)).size() == 1) {
            final GenericDomValue element = domElement.getManager().createStableValue(
              () -> domElement.isValid() ? (GenericDomValue)description.getValues(domElement).get(0) : null);
            doBind(DomUIFactory.createControl(element, commitOnEveryChange(element)), boundComponent);
          }
          else {
            //todo not bound

          }
        }
        else if (description instanceof DomCollectionChildDescription) {
          doBind(DomUIFactory.getDomUIFactory().createCollectionControl(domElement, (DomCollectionChildDescription)description), boundComponent);
        }
      }
    }
    reset();
  }

  protected void doBind(final DomUIControl control, final JComponent boundComponent) {
    myBoundComponents.put(boundComponent, control);
    control.bind(boundComponent);
    addComponent(control);
  }

  private JComponent getBoundComponent(final AbstractDomChildrenDescription description) {
    for (Field field : getClass().getDeclaredFields()) {
      try {
        field.setAccessible(true);

        if (description instanceof DomChildrenDescription) {
          final DomChildrenDescription childrenDescription = (DomChildrenDescription)description;
          if (convertFieldName(field.getName(), childrenDescription).equals(childrenDescription.getXmlElementName()) && field.get(this) instanceof JComponent) {
            return (JComponent)field.get(this);
          }
        }
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
    }

    return null;
  }

  private String convertFieldName(String propertyName, final DomChildrenDescription description) {
    propertyName = StringUtil.trimStart(propertyName, "my");

    String convertedName = description.getDomNameStrategy(getDomElement()).convertName(propertyName);

    if (description instanceof DomCollectionChildDescription) {
      final String unpluralizedStr = StringUtil.unpluralize(convertedName);

      if (unpluralizedStr != null) return unpluralizedStr;
    }
    return convertedName;
  }

  public final Project getProject() {
    return getDomElement().getManager().getProject();
  }

  public final Module getModule() {
    return getDomElement().getModule();
  }

  protected final DomUIControl getDomControl(JComponent component) {
    return myBoundComponents.get(component);
  }
}
