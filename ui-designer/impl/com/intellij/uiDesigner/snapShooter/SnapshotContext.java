/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.snapShooter;

import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.properties.IntroComponentProperty;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.radComponents.RadButtonGroup;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;

import javax.swing.*;
import java.util.*;

/**
 * @author yole
 */
public class SnapshotContext {
  private Palette myPalette;
  private RadRootContainer myRootContainer;
  private Set<ButtonGroup> myButtonGroups = new HashSet<ButtonGroup>();
  private Map<JComponent, RadComponent> myImportMap = new HashMap<JComponent, RadComponent>();

  private static class ComponentProperty {
    public JComponent owner;
    public String name;
    public JComponent value;

    public ComponentProperty(final JComponent owner, final String name, final JComponent value) {
      this.owner = owner;
      this.name = name;
      this.value = value;
    }
  }

  private List<ComponentProperty> myComponentProperties = new ArrayList<ComponentProperty>();

  public SnapshotContext() {
    myPalette = new Palette(null);
    myRootContainer = new RadRootContainer(null, "1");
  }

  public RadRootContainer getRootContainer() {
    return myRootContainer;
  }

  public Palette getPalette() {
    return myPalette;
  }

  public String newId() {
    return FormEditingUtil.generateId(myRootContainer);
  }

  public void registerComponent(final JComponent component, final RadComponent radComponent) {
    myImportMap.put(component, radComponent);
  }

  public void registerButtonGroup(final ButtonGroup group) {
    myButtonGroups.add(group);
  }

  public void postProcess() {
    for(ButtonGroup group: myButtonGroups) {
      RadButtonGroup radButtonGroup = myRootContainer.createGroup(myRootContainer.suggestGroupName());
      Enumeration<AbstractButton> elements = group.getElements();
      while(elements.hasMoreElements()) {
        AbstractButton btn = elements.nextElement();
        RadComponent c = myImportMap.get(btn);
        if (c != null) {
          radButtonGroup.add(c);
        }
      }
    }
    for(ComponentProperty prop: myComponentProperties) {
      RadComponent radOwner = myImportMap.get(prop.owner);
      RadComponent radValue = myImportMap.get(prop.value);
      if (radOwner != null && radValue != null) {
        final IntrospectedProperty property = radOwner.getPalette().getIntrospectedProperty(radOwner, prop.name);
        assert property != null;
        //noinspection unchecked
        IntroComponentProperty icp = (IntroComponentProperty) property;
        try {
          icp.setValue(radOwner, radValue.getId());
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  public void registerComponentProperty(final JComponent component, final String name, final JComponent value) {
    myComponentProperties.add(new ComponentProperty(component, name, value));
  }
}
