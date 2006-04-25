package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.lw.LwButtonGroup;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadRootContainer extends RadContainer implements IRootContainer {
  private String myClassToBind;
  private String myMainComponentBinding;
  private Locale myStringDescriptorLocale;
  private List<RadButtonGroup> myButtonGroups = new ArrayList<RadButtonGroup>();

  public RadRootContainer(final Module module, final String id) {
    super(module, JPanel.class, id);
    getDelegee().setBackground(Color.WHITE);
  }

  /**
   * Always returns <code>false</code> because root group isn't selectable.
   */
  public boolean isSelected() {
    return false;
  }

  /**
   * <code>RadRootContainer</code> is not selectable
   */
  public void setSelected(final boolean ignored) { }

  /**
   * @return full qualified name of the class. If there is no bound class
   * then the method returns <code>null</code>.
   */
  @Nullable
  public String getClassToBind(){
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
      if (myButtonGroups.size() > 0) {
        writer.startElement(UIFormXmlConstants.ELEMENT_BUTTON_GROUPS);
        for(RadButtonGroup group: myButtonGroups) {
          group.write(writer);
        }
        writer.endElement();
      }
    }finally{
      writer.endElement(); // form
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

  @Override @Nullable
  protected RadLayoutManager createInitialLayoutManager() {
    return RadXYLayoutManager.INSTANCE;
  }

  @Nullable public RadButtonGroup findGroupForComponent(@NotNull final RadComponent component) {
    for(RadButtonGroup group: myButtonGroups) {
      if (group.contains(component)) {
        return group;
      }
    }
    return null;
  }

  public void setGroupForComponent(@NotNull RadComponent component, @Nullable RadButtonGroup value) {
    for(RadButtonGroup group: myButtonGroups) {
      if (group == value) {
        group.add(component);
      }
      else {
        group.remove(component);
      }
    }
  }

  public RadButtonGroup[] getAllGroups() {
    return myButtonGroups.toArray(new RadButtonGroup[myButtonGroups.size()]);
  }

  public String suggestGroupName() {
    int groupNumber = 1;
    group: while(true) {
      @NonNls String suggestedName = "buttonGroup" + groupNumber;
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

  public void setButtonGroups(final LwButtonGroup[] buttonGroups) {
    myButtonGroups.clear();
    for(LwButtonGroup lwGroup: buttonGroups) {
      final String[] componentIds = lwGroup.getComponentIds();
      if (componentIds.length > 0) {
        RadButtonGroup group = createGroup(lwGroup.getName());
        group.addComponentIds(componentIds);
      }
    }
  }

  public List<RadComponent> getGroupContents(final RadButtonGroup group) {
    ArrayList<RadComponent> result = new ArrayList<RadComponent>();
    for(String id: group.getComponentIds()) {
      RadComponent component = (RadComponent) FormEditingUtil.findComponent(this, id);
      if (component != null) {
        result.add(component);
      }
    }
    return result;
  }

  public String getButtonGroupName(IComponent component) {
    for(RadButtonGroup group: myButtonGroups) {
      if (group.contains((RadComponent)component)) {
        return group.getName();
      }
    }
    return null;
  }

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
}
