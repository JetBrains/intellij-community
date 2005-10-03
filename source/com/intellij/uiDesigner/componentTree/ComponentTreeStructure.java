package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.GuiEditor;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadContainer;
import com.intellij.uiDesigner.RadRootContainer;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class ComponentTreeStructure extends AbstractTreeStructure{
  private static final Logger LOG=Logger.getInstance("#com.intellij.uiDesigner.componentTree.ComponentPtr");
  private static final Object[] ourEmptyObjectArray=new Object[]{};

  private final Object myRootElement;
  private final GuiEditor myEditor;

  public ComponentTreeStructure(final GuiEditor editor){
    if(editor==null){
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("editor cannot be null");
    }

    myRootElement=new Object();
    myEditor=editor;
  }

  public Object getRootElement(){
    return myRootElement;
  }

  public Object[] getChildElements(final Object element){
    if(element==myRootElement){
      final RadContainer rootContainer=myEditor.getRootContainer();
      return new Object[]{new ComponentPtr(myEditor,rootContainer)};
    }
    else if(element instanceof ComponentPtr){
      final ComponentPtr ptr=(ComponentPtr)element;
      LOG.assertTrue(ptr.isValid()); // pointer must be valid
      final RadComponent component=ptr.getComponent();
      if(component instanceof RadContainer){
        final RadContainer container=(RadContainer)component;
        final ComponentPtr[] ptrs=new ComponentPtr[container.getComponentCount()];
        for(int i=0;i<ptrs.length;i++){
          ptrs[i]=new ComponentPtr(myEditor,container.getComponent(i));
        }
        return ptrs;
      }else{
        return ourEmptyObjectArray;
      }
    }
    else{
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("unknown element: "+element);
    }
  }

  public Object getParentElement(final Object element){
    if(element==myRootElement){
      return null;
    }
    else if(element instanceof ComponentPtr){ // RadContainer is also RadComponent
      final ComponentPtr ptr=(ComponentPtr)element;
      LOG.assertTrue(ptr.isValid()); // pointer must be valid
      final RadComponent component=ptr.getComponent();
      if(component instanceof RadRootContainer){
        return myRootElement;
      }else{
        return new ComponentPtr(myEditor,component.getParent());
      }
    }
    else{
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("unknown element: "+element);
    }
  }

  public NodeDescriptor createDescriptor(final Object element,final NodeDescriptor parentDescriptor){
    if(element==myRootElement){
      return new RootDescriptor(parentDescriptor,myRootElement);
    }
    else if(element instanceof ComponentPtr){
      return new ComponentPtrDescriptor(parentDescriptor,(ComponentPtr)element);
    }
    else{
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("unknown element: "+element);
    }
  }

  /**
   * Only tree root (it's invisible) node and RadRootContainer are auto-expanded
   */
  public boolean isAutoExpandNode(final NodeDescriptor descriptor){
    final Object element=descriptor.getElement();
    return element==myRootElement || element==myEditor.getRootContainer();
  }

  public void commit(){}

  /**
   * Nothing to commit
   */
  public boolean hasSomethingToCommit(){
    return false;
  }
}
