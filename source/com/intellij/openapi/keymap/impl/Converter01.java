package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author Vladimir Kondratyev
 */
public class Converter01{
  /**
   * Converts keymap from version "0" (no version specified)
   * to version "1".
   * @param keymapElement XML element that corresponds to "keymap" tag.
   */
  public static void convert(Element keymapElement) throws InvalidDataException{
    if(!"keymap".equals(keymapElement.getName())){
      throw new IllegalArgumentException("unknown element: "+keymapElement);
    }
    String version=keymapElement.getAttributeValue("version");
    if(version!=null){
      throw new InvalidDataException("unknown version: "+version);
    }

    // Add version

    keymapElement.setAttribute("version",Integer.toString(1));

    // disableMnemonics -> disable-mnemonics

    boolean disableMnemonics=Boolean.valueOf("disableMnemonics").booleanValue();
    keymapElement.removeAttribute("disableMnemonics");
    keymapElement.setAttribute("disable-mnemonics",Boolean.toString(disableMnemonics));

    // Now we have to separate all shortcuts by action's ID and convert binding to keyboard-shortcut

    String name=keymapElement.getAttributeValue("name");
    if(name==null){
      throw new InvalidDataException("Attribute 'name' of <keymap> must be specified");
    }
    HashMap id2elements=new HashMap();

    for(Iterator i=keymapElement.getChildren().iterator();i.hasNext();){
      Element oldChild=(Element)i.next();
      if("binding".equals(oldChild.getName())){ // binding -> keyboard-shortcut
        // Remove attribute "id"
        String id=oldChild.getAttributeValue("id");
        if(id==null){
          throw new InvalidDataException("attribute 'id' must be specified");
        }
        // keystroke -> first-keystroke
        String keystroke=oldChild.getAttributeValue("keystroke");
        // suffix -> second-keystroke
        String suffix=oldChild.getAttributeValue("suffix");
        if(keystroke!=null){
          Element newChild=new Element("keyboard-shortcut");
          newChild.setAttribute("first-keystroke",keystroke);
          if(suffix!=null){
            newChild.setAttribute("second-keystroke",suffix);
          }
          // Put new child into the map
          ArrayList elements=(ArrayList)id2elements.get(id);
          if(elements==null){
            elements=new ArrayList(2);
            id2elements.put(id,elements);
          }
          elements.add(newChild);
        }else{
          id2elements.put(id,new ArrayList(0));
        }
        // Remove old child
        i.remove();
      }else if("mouse-shortcut".equals(oldChild.getName())){
        // Remove attribute "id"
        String id=oldChild.getAttributeValue("id");
        if(id==null){
          throw new InvalidDataException("Attribute 'id' of <mouse-shortcut> must be specified; keymap name="+name);
        }
        oldChild.removeAttribute("id");
        // Remove old child
        i.remove();
        // Put new child into the map
        ArrayList elements=(ArrayList)id2elements.get(id);
        if(elements==null){
          elements=new ArrayList(2);
          id2elements.put(id,elements);
        }
        elements.add(oldChild);
      }else{
        throw new InvalidDataException("unknown element : "+oldChild.getName());
      }
    }

    for(Iterator i=id2elements.keySet().iterator();i.hasNext();){
      String id=(String)i.next();
      Element actionElement=new Element("action");
      actionElement.setAttribute("id",id);
      ArrayList elements=(ArrayList)id2elements.get(id);
      for(Iterator j=elements.iterator();j.hasNext();){
        Element newChild=(Element)j.next();
        actionElement.addContent(newChild);
      }
      keymapElement.addContent(actionElement);
    }
  }
}
