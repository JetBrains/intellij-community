package com.intellij.uiDesigner;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.IRootContainer;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RadRootContainer extends RadContainer implements IRootContainer{
  private String myClassToBind;
  private String myMainComponentBinding;

  public RadRootContainer(final Module module, final Class aClass, final String id){
    super(module, aClass, id);
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
    }finally{
      writer.endElement(); // form
    }
  }
}
