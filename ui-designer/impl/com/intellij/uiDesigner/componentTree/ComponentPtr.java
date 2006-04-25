package com.intellij.uiDesigner.componentTree;

import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ComponentPtr{
  private final GuiEditor myEditor;
  private final String myId;
  private RadComponent myComponent;

  /**
   * @param component
   */
  public ComponentPtr(@NotNull final GuiEditor editor, @NotNull final RadComponent component){
    myEditor=editor;
    myId=component.getId();

    validate();
    if(!isValid()){
      throw new IllegalArgumentException("invalid component: "+component);
    }
  }

  /**
   * @return <code>RadComponent</code> which was calculated by the last
   * <code>validate</code> method.
   */
  public RadComponent getComponent(){
    return myComponent;
  }

  /**
   * @return <code>true</code> if and only if the pointer is valid.
   * It means that last <code>validate</code> call was successful and
   * pointer refers to live component.
   */
  public boolean isValid(){
    return myComponent!=null;
  }

  /**
   * Validates (updates) the state of the pointer
   */
  public void validate(){
    // Try to find component with myId starting from root container
    final RadContainer container=myEditor.getRootContainer();
    myComponent= (RadComponent)FormEditingUtil.findComponent(container,myId);
  }

  public boolean equals(final Object obj){
    if(!(obj instanceof ComponentPtr)){
      return false;
    }
    return myId.equals(((ComponentPtr)obj).myId);
  }

  public int hashCode(){
    return myId.hashCode();
  }
}
