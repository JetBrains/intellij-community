package com.intellij.util.xml.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;

/**
 * User: Sergey.Vasiliev
 * Date: Nov 17, 2005
 */
public abstract class BasicDomElementComponent<T extends DomElement> extends AbstractDomElementComponent<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.ui.editors.BasicDomElementComponent");

  public BasicDomElementComponent(T domElement) {
    super(domElement);
  }

  protected void bindProperties() {
    if (getDomElement() == null) return;

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
    reset();
  }

  protected void doBind(final DomUIControl control, final JComponent boundComponent) {
    control.bind(boundComponent);
    addComponent(control);
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

  protected void setEnabled(Component component, boolean enabled) {
    component.setEnabled(enabled);
    if (component instanceof Container) {
      for (Component child : ((Container)component).getComponents()) {
        setEnabled(child, enabled);
      }
    }

  }
}
