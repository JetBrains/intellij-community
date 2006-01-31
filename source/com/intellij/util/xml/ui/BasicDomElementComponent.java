package com.intellij.util.xml.ui;

import com.intellij.j2ee.j2eeDom.xmlData.ReadOnlyDeploymentDescriptorModificationException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.project.Project;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ui.DomUIFactory;
import com.intellij.util.xml.ui.DomUIControl;
import com.intellij.util.xml.ui.AbstractDomElementComponent;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;

import javax.swing.*;
import java.awt.event.FocusListener;
import java.awt.event.FocusEvent;
import java.lang.reflect.Field;

/**
 * User: Sergey.Vasiliev
 * Date: Nov 17, 2005
 */
public abstract class BasicDomElementComponent extends AbstractDomElementComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.ui.editors.BasicDomElementComponent");

  public BasicDomElementComponent(DomElement domElement) {
    super(domElement);
  }

  protected void bindProperties() {
    final java.util.List<DomChildrenDescription> childrenDescriptions = getDomElement().getGenericInfo().getChildrenDescriptions();
    for (DomChildrenDescription description : childrenDescriptions) {
      final JComponent boundComponent = getBoundComponent(description);
      if (boundComponent != null) {
        if (description instanceof DomFixedChildDescription && DomUtil.isGenericValueType(description.getType())) {
          final java.util.List<GenericDomValue> values = (java.util.List<GenericDomValue>)description.getValues(getDomElement());
          if (values.size() == 1) {
            final DomUIControl control = DomUIFactory.createControl(values.get(0));

            doBind(control, boundComponent);
          }
          else {
            //todo not bound
            for (int i = 0; i < values.size(); i++) {

            }
          }
        }
        else if (description instanceof DomCollectionChildDescription) {
          DomUIControl control = DomUIFactory.createCollectionControl(getDomElement(), (DomCollectionChildDescription)description);
          doBind(control, boundComponent);
        }
      }
    }
  }

  protected void doBind(final DomUIControl control, final JComponent boundComponent) {
    control.bind(boundComponent);
    addComponent(control);

    if (control.getFocusedComponent() != null) {
      control.getFocusedComponent().addFocusListener(new FocusListener() {
        public void focusGained(FocusEvent e) {
        }

        public void focusLost(FocusEvent e) {
          if (!e.isTemporary()) {
            try {
              commit();
            }
            catch (ReadOnlyDeploymentDescriptorModificationException e1) {
              LOG.error(e1);
            }
          }
        }
      });
    }

    control.reset();
  }

  private JComponent getBoundComponent(final DomChildrenDescription description) {
    final Field[] fields = this.getClass().getDeclaredFields();
    for (Field field : fields) {
      try {
        field.setAccessible(true);

        if (convertFieldName(field.getName(), description).equals(description.getXmlElementName()) && field.get(this) instanceof JComponent)
        {
          return (JComponent)field.get(this);
        }
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
    }

    return null;
  }

  private String convertFieldName(String propertyName, final DomChildrenDescription description) {
    if (propertyName.startsWith("my")) propertyName = propertyName.substring(2);

    String convertedName = description.getDomNameStrategy(getDomElement()).convertName(propertyName);

    if (description instanceof DomCollectionChildDescription) {
      final String unpluralizedStr = StringUtil.unpluralize(convertedName);

      if (unpluralizedStr != null) return unpluralizedStr;
    }
    return convertedName;
  }

  protected Project getProject() {
    return getDomElement().getManager().getProject();
  }
}
