// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.ui.JBColor;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.ModuleProvider;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.IButtonGroup;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.lw.LwInspectionSuppression;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public final class RadRootContainer extends RadContainer implements IRootContainer {
  private String myClassToBind;
  private String myMainComponentBinding;
  private Locale myStringDescriptorLocale;
  private final List<RadButtonGroup> myButtonGroups = new ArrayList<>();
  private final List<LwInspectionSuppression> myInspectionSuppressions = new ArrayList<>();

  public RadRootContainer(final ModuleProvider module, final String id) {
    super(module, RootPanel.class, id);
    getDelegee().setBackground(new JBColor(Color.WHITE, UIUtil.getListBackground()));
  }

  /**
   * Always returns {@code false} because root group isn't selectable.
   */
  @Override
  public boolean isSelected() {
    return false;
  }

  /**
   * {@code RadRootContainer} is not selectable
   */
  @Override
  public void setSelected(final boolean ignored) { }

  /**
   * @return full qualified name of the class. If there is no bound class
   * then the method returns {@code null}.
   */
  @Override
  public @Nullable
  @NlsSafe String getClassToBind() {
    return myClassToBind;
  }

  public void setClassToBind(final String classToBind){
    myClassToBind = classToBind;
  }

  public String getMainComponentBinding(){
    return myMainComponentBinding;
  }

  public void setMainComponentBinding(final String mainComponentBinding){
    myMainComponentBinding = mainComponentBinding;
  }

  @Override
  public void write(final XmlWriter writer) {
    writer.startElement("form", Utils.FORM_NAMESPACE);
    try{
      writer.addAttribute("version", 1);
      final String classToBind = getClassToBind();
      if (classToBind != null){
        writer.addAttribute("bind-to-class", classToBind);
      }
      final String mainComponentBinding = getMainComponentBinding();
      if (mainComponentBinding != null) {
        writer.addAttribute("stored-main-component-binding", mainComponentBinding);
      }
      writeChildrenImpl(writer);
      if (!myButtonGroups.isEmpty()) {
        writer.startElement(UIFormXmlConstants.ELEMENT_BUTTON_GROUPS);
        for(RadButtonGroup group: myButtonGroups) {
          group.write(writer);
        }
        writer.endElement();
      }
      writeInspectionSuppressions(writer);
    }
    finally{
      writer.endElement(); // form
    }
  }

  private void writeInspectionSuppressions(final XmlWriter writer) {
    if (!myInspectionSuppressions.isEmpty()) {
      writer.startElement(UIFormXmlConstants.ELEMENT_INSPECTION_SUPPRESSIONS);
      for(LwInspectionSuppression suppression: myInspectionSuppressions) {
        writer.startElement(UIFormXmlConstants.ELEMENT_SUPPRESS);
        writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_INSPECTION, suppression.getInspectionId());
        if (suppression.getComponentId() != null) {
          writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_ID, suppression.getComponentId());
        }
        writer.endElement();
      }
      writer.endElement();
    }
  }

  @Override public void writeConstraints(final XmlWriter writer) {
    writer.startElement("constraints");
    try {
      myLayoutManager.writeChildConstraints(writer, this);
    } finally {
      writer.endElement(); // constraints
    }
  }

  @Override
  protected @Nullable RadLayoutManager createInitialLayoutManager() {
    return RadXYLayoutManager.INSTANCE;
  }

  public void setGroupForComponent(@NotNull RadComponent component, @Nullable RadButtonGroup value) {
    for(int i=myButtonGroups.size()-1; i >= 0; i--) {
      RadButtonGroup group = myButtonGroups.get(i);
      if (group == value) {
        group.add(component);
      }
      else {
        group.remove(component);
        if (group.isEmpty()) {
          myButtonGroups.remove(i);
        }
      }
    }
  }

  @Override
  public RadButtonGroup[] getButtonGroups() {
    return myButtonGroups.toArray(new RadButtonGroup[0]);
  }

  public @NlsSafe String suggestGroupName() {
    int groupNumber = 1;
    group: while(true) {
      @NonNls String suggestedName = "buttonGroup" + groupNumber;
      // we don't have a project in snapshooter
      if (getModule() != null) {
        SuggestedNameInfo nameInfo =
          JavaCodeStyleManager.getInstance(getProject()).suggestVariableName(VariableKind.FIELD, suggestedName, null, null);
        suggestedName = nameInfo.names[0];
      }
      for(RadButtonGroup group: myButtonGroups) {
        if (group.getName().equals(suggestedName)) {
          groupNumber++;
          continue group;
        }
      }
      return suggestedName;
    }
  }

  public RadButtonGroup createGroup(final String groupName) {
    RadButtonGroup group = new RadButtonGroup(groupName);
    myButtonGroups.add(group);
    return group;
  }

  public void deleteGroup(RadButtonGroup group) {
    myButtonGroups.remove(group);
  }

  public void setButtonGroups(final IButtonGroup[] buttonGroups) {
    myButtonGroups.clear();
    for(IButtonGroup lwGroup: buttonGroups) {
      final String[] componentIds = lwGroup.getComponentIds();
      if (componentIds.length > 0) {
        RadButtonGroup group = createGroup(lwGroup.getName());
        group.setBound(lwGroup.isBound());
        group.addComponentIds(componentIds);
      }
    }
  }

  public List<RadComponent> getGroupContents(final RadButtonGroup group) {
    ArrayList<RadComponent> result = new ArrayList<>();
    for(String id: group.getComponentIds()) {
      RadComponent component = (RadComponent) FormEditingUtil.findComponent(this, id);
      if (component != null) {
        result.add(component);
      }
    }
    return result;
  }

  @Override
  public String getButtonGroupName(IComponent component) {
    for(RadButtonGroup group: myButtonGroups) {
      if (group.contains((RadComponent)component)) {
        return group.getName();
      }
    }
    return null;
  }

  @Override
  public String[] getButtonGroupComponentIds(String groupName) {
    for(RadButtonGroup group: myButtonGroups) {
      if (group.getName().equals(groupName)) {
        return group.getComponentIds();
      }
    }
    throw new IllegalArgumentException("Cannot find group " + groupName);
  }

  public Locale getStringDescriptorLocale() {
    return myStringDescriptorLocale;
  }

  public void setStringDescriptorLocale(final Locale stringDescriptorLocale) {
    myStringDescriptorLocale = stringDescriptorLocale;
  }

  public void suppressInspection(String inspectionId, @Nullable RadComponent component) {
    for(int i=myInspectionSuppressions.size()-1; i >= 0; i--) {
      LwInspectionSuppression suppression = myInspectionSuppressions.get(i);
      if (suppression.getInspectionId().equals(inspectionId)) {
        if (component != null && (component.getId().equals(suppression.getComponentId()) || suppression.getComponentId() == null)) {
          return;
        }
        if (component == null && suppression.getComponentId() != null) {
          myInspectionSuppressions.remove(i);
        }
      }
    }
    myInspectionSuppressions.add(new LwInspectionSuppression(inspectionId, component == null ? null : component.getId()));
  }

  @Override
  public boolean isInspectionSuppressed(final String inspectionId, final String componentId) {
    for(LwInspectionSuppression suppression: myInspectionSuppressions) {
      if ((suppression.getComponentId() == null || suppression.getComponentId().equals(componentId)) &&
          suppression.getInspectionId().equals(inspectionId)) {
        return true;
      }
    }
    return false;
  }

  public LwInspectionSuppression[] getInspectionSuppressions() {
    return myInspectionSuppressions.toArray(LwInspectionSuppression.EMPTY_ARRAY);
  }

  public void setInspectionSuppressions(final LwInspectionSuppression[] inspectionSuppressions) {
    myInspectionSuppressions.clear();
    Collections.addAll(myInspectionSuppressions, inspectionSuppressions);
  }

  public void removeInspectionSuppression(final LwInspectionSuppression suppression) {
    for(LwInspectionSuppression existing: myInspectionSuppressions) {
      if (existing.getInspectionId().equals(suppression.getInspectionId()) &&
          Objects.equals(existing.getComponentId(), suppression.getComponentId())) {
        myInspectionSuppressions.remove(existing);
        break;
      }
    }
  }

  public static class RootPanel extends JPanel {
    // public constructor for reflection
    public RootPanel() {
    }

    @Override
    public void doLayout() {
      super.doLayout();
      int count = getComponentCount();
      for (int i = 0; i < count; i++) {
        Component component = getComponent(i);
        Rectangle bounds = component.getBounds();
        if (bounds.x < 0 || bounds.y < 0) {
          component.setBounds(bounds.x < 0 ? 20 : bounds.x, bounds.y < 0 ? 20 : bounds.y, bounds.width, bounds.height);
        }
      }
    }
  }
}
