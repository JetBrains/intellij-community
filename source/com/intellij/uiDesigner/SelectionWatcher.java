package com.intellij.uiDesigner;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class SelectionWatcher {
  private final MyPropertyChangeListener myChangeListener;

  public SelectionWatcher(){
    myChangeListener = new MyPropertyChangeListener();
  }

  public final void install(final RadComponent component){
    if (component == null) {
      throw new IllegalArgumentException("component cannot be null");
    }
    component.addPropertyChangeListener(myChangeListener);
    if(component instanceof RadContainer){
      final RadContainer container = (RadContainer)component;
      for(int i = container.getComponentCount() - 1; i>= 0; i--){
        install(container.getComponent(i));
      }
    }
  }

  public final void deinstall(final RadComponent component){
    if (component == null) {
      throw new IllegalArgumentException("component cannot be null");
    }
    component.removePropertyChangeListener(myChangeListener);
    if(component instanceof RadContainer){
      final RadContainer container = (RadContainer)component;
      for(int i = container.getComponentCount() - 1; i>= 0; i--){
        deinstall(container.getComponent(i));
      }
    }
  }

  protected abstract void selectionChanged(RadComponent component, boolean selected);

  private final class MyPropertyChangeListener implements PropertyChangeListener{
    public void propertyChange(final PropertyChangeEvent e) {
      if(RadComponent.PROP_SELECTED.equals(e.getPropertyName())){
        final Boolean selected = (Boolean)e.getNewValue();
        selectionChanged((RadComponent)e.getSource(), selected.booleanValue());
      }
      else if(RadContainer.PROP_CHILDREN.equals(e.getPropertyName())){
        final RadComponent[] oldChildren = (RadComponent[])e.getOldValue();
        for(int i = oldChildren.length - 1; i >= 0; i--){
          deinstall(oldChildren[i]);
        }

        final RadComponent[] newChildren = (RadComponent[])e.getNewValue();
        for(int i = newChildren.length - 1; i >= 0; i--){
          install(newChildren[i]);
        }
      }
    }
  }
}
