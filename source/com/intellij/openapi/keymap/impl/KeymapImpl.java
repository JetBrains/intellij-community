package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import org.jdom.Element;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 * @author Eugene Belyaev
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class KeymapImpl implements Keymap {
  private static final Logger LOG = Logger.getInstance("#com.intellij.keymap.KeymapImpl");

  private String myName;
  private KeymapImpl myParent;
  private boolean myCanModify = true;
  private boolean myDisableMnemonics = false;

  private THashMap myActionId2ListOfShortcuts = new THashMap();

  /**
   * Don't use this field directly! Use it only through <code>getKeystroke2ListOfIds</code>.
   */
  private THashMap myKeystroke2ListOfIds = null;
  // TODO[vova,anton] it should be final member

  /**
   * Don't use this field directly! Use it only through <code>getMouseShortcut2ListOfIds</code>.
   */
  private THashMap myMouseShortcut2ListOfIds = null;
  // TODO[vova,anton] it should be final member

  private static HashMap ourNamesForKeycodes = null;
  private static final Shortcut[] ourEmptyShortcutsArray = new Shortcut[0];
  private final ArrayList<Keymap.Listener> myListeners = new ArrayList<Keymap.Listener>();

  static {
    ourNamesForKeycodes = new HashMap();
    try {
      Field[] fields = KeyEvent.class.getDeclaredFields();
      for(int i = 0; i < fields.length; i++){
        Field field = fields[i];
        String fieldName = field.getName();
        if(fieldName.startsWith("VK_")) {
          int keyCode = field.getInt(KeyEvent.class);
          ourNamesForKeycodes.put(new Integer(keyCode), fieldName.substring(3));
        }
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public String getName() {
    return myName;
  }

  public String getPresentableName() {
    return getName();
  }

  public void setName(String name) {
    myName = name;
  }


  public KeymapImpl deriveKeymap() {
    if (!canModify()) {
      KeymapImpl newKeymap = new KeymapImpl();

      newKeymap.myParent = this;
      newKeymap.myName = null;
      newKeymap.myDisableMnemonics = myDisableMnemonics;
      newKeymap.myCanModify = canModify();
      return newKeymap;
    }
    else {
      return copy();
    }
  }

  public KeymapImpl copy() {
    KeymapImpl newKeymap = new KeymapImpl();
    newKeymap.myParent = myParent;
    newKeymap.myName = myName;
    newKeymap.myDisableMnemonics = myDisableMnemonics;
    newKeymap.myCanModify = canModify();

    newKeymap.myKeystroke2ListOfIds = null;
    newKeymap.myMouseShortcut2ListOfIds = null;

    THashMap actionsIdsToListOfShortcuts = new THashMap();
    for(Iterator i=myActionId2ListOfShortcuts.keySet().iterator();i.hasNext();){
      Object key = i.next();
      ArrayList list = (ArrayList)myActionId2ListOfShortcuts.get(key);
      actionsIdsToListOfShortcuts.put(key, list.clone());
    }

    newKeymap.myActionId2ListOfShortcuts = actionsIdsToListOfShortcuts;

    return newKeymap;
  }

  public boolean equals(Object object) {
    if (!(object instanceof Keymap)) return false;
    KeymapImpl secondKeymap = (KeymapImpl)object;
    if (!Comparing.equal(myName, secondKeymap.myName)) return false;
    if (myDisableMnemonics != secondKeymap.myDisableMnemonics) return false;
    if (myCanModify != secondKeymap.myCanModify) return false;
    if (!Comparing.equal(myParent, secondKeymap.myParent)) return false;
    if (!Comparing.equal(myActionId2ListOfShortcuts, secondKeymap.myActionId2ListOfShortcuts)) return false;
    return true;
  }

  public int hashCode(){
    int hashCode=0;
    if(myName!=null){
      hashCode+=myName.hashCode();
    }
    return hashCode;
  }

  public Keymap getParent() {
    return myParent;
  }

  public boolean canModify() {
    return myCanModify;
  }

  public void setCanModify(boolean val) {
    myCanModify = val;
  }

  protected Shortcut[] getParentShortcuts(String actionId) {
    return myParent.getShortcuts(actionId);
  }

  public void addShortcut(String actionId, Shortcut shortcut) {
    addShortcutSilently(actionId, shortcut);
    fireShortcutChanged(actionId);
  }

  private void addShortcutSilently(String actionId, Shortcut shortcut) {
    ArrayList list = (ArrayList)myActionId2ListOfShortcuts.get(actionId);
    if (list == null) {
      list = new ArrayList();
      myActionId2ListOfShortcuts.put(actionId, list);
      if (myParent != null) {
        // copy parent shortcuts for this actionId
        Shortcut[] shortcuts = getParentShortcuts(actionId);
        for (int i = 0; i < shortcuts.length; i++) {
          // shortcuts are immutables
          list.add(shortcuts[i]);
        }
      }
    }
    list.add(shortcut);

    if (myParent != null && areShortcutsEqual(getParentShortcuts(actionId), getShortcuts(actionId))) {
      myActionId2ListOfShortcuts.remove(actionId);
    }
    myKeystroke2ListOfIds = null;
    myMouseShortcut2ListOfIds = null;
  }

  public void removeAllActionShortcuts(String actionId) {
    Shortcut[] allShortcuts = getShortcuts(actionId);
    for (int i = 0; i < allShortcuts.length; i++) {
      Shortcut shortcut = allShortcuts[i];
      removeShortcut(actionId, shortcut);
    }
  }

  public void removeShortcut(String actionId, Shortcut shortcut) {
    ArrayList list = (ArrayList)myActionId2ListOfShortcuts.get(actionId);
    if (list != null) {
      for(int i=0; i<list.size(); i++) {
        if(shortcut.equals(list.get(i))) {
          list.remove(i);
          if (myParent != null && areShortcutsEqual(getParentShortcuts(actionId), getShortcuts(actionId))) {
            myActionId2ListOfShortcuts.remove(actionId);
          }
          break;
        }
      }
    }
    else if (myParent != null) {
      // put to the map the parent's bindings except for the removed binding
      Shortcut[] parentShortcuts = getParentShortcuts(actionId);
      ArrayList listOfShortcuts = new ArrayList();
      for (int i = 0; i < parentShortcuts.length; i++) {
        if(!shortcut.equals(parentShortcuts[i])) {
          listOfShortcuts.add(parentShortcuts[i]);
        }
      }
      myActionId2ListOfShortcuts.put(actionId, listOfShortcuts);
    }
    myKeystroke2ListOfIds = null;
    myMouseShortcut2ListOfIds = null;
    fireShortcutChanged(actionId);
  }

  private THashMap getKeystroke2ListOfIds() {
    if (myKeystroke2ListOfIds == null) {
      myKeystroke2ListOfIds = new THashMap();

      for (Iterator ids = myActionId2ListOfShortcuts.keySet().iterator(); ids.hasNext();) {
        String id = (String)ids.next();
        ArrayList listOfShortcuts = (ArrayList)myActionId2ListOfShortcuts.get(id);
        for (int i=0; i<listOfShortcuts.size(); i++) {
          Shortcut shortcut = (Shortcut)listOfShortcuts.get(i);
          if (!(shortcut instanceof KeyboardShortcut)) {
            continue;
          }
          KeyStroke firstKeyStroke = ((KeyboardShortcut)shortcut).getFirstKeyStroke();
          ArrayList listOfIds = (ArrayList)myKeystroke2ListOfIds.get(firstKeyStroke);
          if (listOfIds == null) {
            listOfIds = new ArrayList();
            myKeystroke2ListOfIds.put(firstKeyStroke, listOfIds);
          }
          // action may have more that 1 shortcut with same first keystroke
          if (!listOfIds.contains(id)) {
            listOfIds.add(id);
          }
        }
      }
    }
    return myKeystroke2ListOfIds;
  }

  private THashMap getMouseShortcut2ListOfIds(){
    if(myMouseShortcut2ListOfIds==null){
      myMouseShortcut2ListOfIds=new THashMap();

      for (Iterator ids = myActionId2ListOfShortcuts.keySet().iterator(); ids.hasNext();) {
        String id = (String)ids.next();
        ArrayList listOfShortcuts = (ArrayList)myActionId2ListOfShortcuts.get(id);
        for (int i=0; i<listOfShortcuts.size(); i++) {
          Shortcut shortcut = (Shortcut)listOfShortcuts.get(i);
          if (!(shortcut instanceof MouseShortcut)) {
            continue;
          }
          ArrayList listOfIds = (ArrayList)myMouseShortcut2ListOfIds.get(shortcut);
          if (listOfIds == null) {
            listOfIds = new ArrayList();
            myMouseShortcut2ListOfIds.put(shortcut, listOfIds);
          }
          // action may have more that 1 shortcut with same first keystroke
          if (!listOfIds.contains(id)) {
            listOfIds.add(id);
          }
        }
      }
    }
    return myMouseShortcut2ListOfIds;
  }

  protected String[] getParentActionIds(KeyStroke firstKeyStroke) {
    return myParent.getActionIds(firstKeyStroke);
  }

  public String[] getActionIds(KeyStroke firstKeyStroke) {
    // first, get keystrokes from own map
    ArrayList<String> list = (ArrayList<String>)getKeystroke2ListOfIds().get(firstKeyStroke);
    if (myParent != null) {
      String[] ids = getParentActionIds(firstKeyStroke);
      if (ids.length > 0) {
        boolean originalListInstance = true;
        for (int i = 0; i < ids.length; i++) {
          String id = ids[i];
          // add actions from parent keymap only if they are absent in this keymap
          if (!myActionId2ListOfShortcuts.containsKey(id)) {
            if (list == null) {
              list = new ArrayList<String>();
              originalListInstance = false;
            } else if (originalListInstance) {
              list = (ArrayList<String>)list.clone();
            }
            list.add(id);
          }
        }
      }
    }
    if (list == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    return sortInOrderOfRegistration(list.toArray(new String[list.size()]));
  }

  public String[] getActionIds(KeyStroke firstKeyStroke, KeyStroke secondKeyStroke) {
    String[] ids = getActionIds(firstKeyStroke);
    ArrayList<String> actualBindings = new ArrayList<String>();
    for (int i = 0; i < ids.length; i++) {
      String id = ids[i];
      Shortcut[] shortcuts = getShortcuts(id);
      for (int j = 0; j < shortcuts.length; j++) {
        Shortcut shortcut = shortcuts[j];
        if (!(shortcut instanceof KeyboardShortcut)) {
          continue;
        }
        if (
          Comparing.equal(firstKeyStroke, ((KeyboardShortcut)shortcut).getFirstKeyStroke()) &&
          Comparing.equal(secondKeyStroke, ((KeyboardShortcut)shortcut).getSecondKeyStroke())
        ) {
          actualBindings.add(id);
          break;
        }
      }
    }
    return actualBindings.toArray(new String[actualBindings.size()]);
  }

  protected String[] getParentActionIds(MouseShortcut shortcut) {
    return myParent.getActionIds(shortcut);
  }

  public String[] getActionIds(MouseShortcut shortcut){
    // first, get shortcuts from own map
    ArrayList<String> list = (ArrayList<String>)getMouseShortcut2ListOfIds().get(shortcut);
    if (myParent != null) {
      String[] ids = getParentActionIds(shortcut);
      if (ids.length > 0) {
        boolean originalListInstance = true;
        for (int i = 0; i < ids.length; i++) {
          String id = ids[i];
          // add actions from parent keymap only if they are absent in this keymap
          if (!myActionId2ListOfShortcuts.containsKey(id)) {
            if (list == null) {
              list = new ArrayList<String>();
              originalListInstance = false;
            }else if (originalListInstance) {
              list = (ArrayList<String>)list.clone();
            }
            list.add(id);
          }
        }
      }
    }
    if (list == null){
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    return sortInOrderOfRegistration(list.toArray(new String[list.size()]));
  }

  private static String[] sortInOrderOfRegistration(String[] ids) {
    Arrays.sort(ids, ActionManagerEx.getInstanceEx().getRegistrationOrderComparator());
    return ids;
  }

  public Shortcut[] getShortcuts(String actionId) {
    ArrayList<Shortcut> shortcuts = (ArrayList<Shortcut>)myActionId2ListOfShortcuts.get(actionId);
    if (shortcuts == null) {
      if (myParent != null) {
        return getParentShortcuts(actionId);
      }else{
        return ourEmptyShortcutsArray;
      }
    }
    return shortcuts.toArray(new Shortcut[shortcuts.size()]);
  }

  /**
   * @param keymapElement element which corresponds to "keymap" tag.
   */
  public void readExternal(Element keymapElement, Keymap[] existingKeymaps) throws InvalidDataException {
    // Check and convert parameters
    if(!"keymap".equals(keymapElement.getName())){
      throw new InvalidDataException("unknown element: "+keymapElement);
    }
    if(keymapElement.getAttributeValue("version")==null){
      Converter01.convert(keymapElement);
    }
    //
    String parentName = keymapElement.getAttributeValue("parent");
    if(parentName != null) {
      for(int i = 0; i < existingKeymaps.length; i++) {
        Keymap existingKeymap = existingKeymaps[i];
        if(parentName.equals(existingKeymap.getName())) {
          myParent = (KeymapImpl)existingKeymap;
          myCanModify = true;
          break;
        }
      }
    }
    myName = keymapElement.getAttributeValue("name");

    myDisableMnemonics = "true".equals(keymapElement.getAttributeValue("disable-mnemonics"));
    HashMap id2shortcuts=new HashMap();
    for (Iterator i = keymapElement.getChildren().iterator(); i.hasNext();) {
      Element actionElement=(Element)i.next();
      if("action".equals(actionElement.getName())){
        String id = actionElement.getAttributeValue("id");
        if(id==null){
          throw new InvalidDataException("Attribute 'id' cannot be null; Keymap's name="+myName);
        }
        id2shortcuts.put(id,new ArrayList(1));
        for(Iterator j=actionElement.getChildren().iterator();j.hasNext();){
          Element shortcutElement=(Element)j.next();
          if ("keyboard-shortcut".equals(shortcutElement.getName())){

            // Parse first keystroke

            KeyStroke firstKeyStroke;
            String firstKeyStrokeStr = shortcutElement.getAttributeValue("first-keystroke");
            if(firstKeyStrokeStr != null) {
              firstKeyStroke = ActionManagerEx.getKeyStroke(firstKeyStrokeStr);
              if(firstKeyStroke==null){
                throw new InvalidDataException(
                  "Cannot parse first-keystroke: '" + firstKeyStrokeStr+"'; "+
                    "Action's id="+id+"; Keymap's name="+myName
                );
              }
            }else{
              throw new InvalidDataException(
                "Attribute 'first-keystroke' cannot be null; Action's id="+id+"; Keymap's name="+myName
              );
            }

            // Parse second keystroke

            KeyStroke secondKeyStroke = null;
            String secondKeyStrokeStr=shortcutElement.getAttributeValue("second-keystroke");
            if (secondKeyStrokeStr!=null) {
              secondKeyStroke = ActionManagerEx.getKeyStroke(secondKeyStrokeStr);
              if (secondKeyStroke == null) {
                throw new InvalidDataException(
                  "Wrong second-keystroke: '" + secondKeyStrokeStr+"'; Action's id="+id+"; Keymap's name="+myName
                );
              }
            }
            Shortcut shortcut = new KeyboardShortcut(firstKeyStroke, secondKeyStroke);
            ArrayList shortcuts=(ArrayList)id2shortcuts.get(id);
            shortcuts.add(shortcut);
          }else if("mouse-shortcut".equals(shortcutElement.getName())){
            String keystrokeString=shortcutElement.getAttributeValue("keystroke");
            if (keystrokeString == null) {
              throw new InvalidDataException(
                "Attribute 'keystroke' cannot be null; Action's id=" + id + "; Keymap's name="+myName
              );
            }

            try{
              MouseShortcut shortcut=KeymapUtil.parseMouseShortcut(keystrokeString);
              ArrayList shortcuts=(ArrayList)id2shortcuts.get(id);
              shortcuts.add(shortcut);
            }catch(InvalidDataException exc){
              throw new InvalidDataException(
                "Wrong mouse-shortcut: '"+keystrokeString+"'; Action's id="+id+"; Keymap's name="+myName
              );
            }
          }else{
            throw new InvalidDataException("unknown element: "+shortcutElement+"; Keymap's name="+myName);
          }
        }
      }else{
        throw new InvalidDataException("unknown element: "+actionElement+"; Keymap's name="+myName);
      }
    }
    // Add read shortcuts
    for(Iterator i=id2shortcuts.keySet().iterator();i.hasNext();){
      String id=(String)i.next();
      myActionId2ListOfShortcuts.put(id,new ArrayList(2)); // It's a trick! After that paren's shortcuts are not added to the keymap
      ArrayList shortcuts=(ArrayList)id2shortcuts.get(id);
      for(Iterator j=shortcuts.iterator();j.hasNext();){
        Shortcut shortcut=(Shortcut)j.next();
        addShortcutSilently(id,shortcut);
      }
    }
  }

  public Element writeExternal() {
    Element keymapElement = new Element("keymap");
    keymapElement.setAttribute("version",Integer.toString(1));
    keymapElement.setAttribute("name", myName);
    keymapElement.setAttribute("disable-mnemonics", myDisableMnemonics ? "true" : "false");
    if(myParent != null) {
      keymapElement.setAttribute("parent", myParent.getName());
    }
    String[] ownActionIds = getOwnActionIds();
    Arrays.sort(ownActionIds);
    for(int i = 0; i < ownActionIds.length; i++){
      String actionId = ownActionIds[i];
      Element actionElement=new Element("action");
      actionElement.setAttribute("id",actionId);
      // Save keyboad shortcuts
      Shortcut[] shortcuts = getShortcuts(actionId);
      for(int j = 0; j < shortcuts.length; j++){
        Shortcut shortcut = shortcuts[j];
        if (shortcut instanceof KeyboardShortcut) {
          KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;
          Element element = new Element("keyboard-shortcut");
          element.setAttribute("first-keystroke", getKeyShortcutString(keyboardShortcut.getFirstKeyStroke()));
          if (keyboardShortcut.getSecondKeyStroke() != null){
            element.setAttribute("second-keystroke", getKeyShortcutString(keyboardShortcut.getSecondKeyStroke()));
          }
          actionElement.addContent(element);
        } else if (shortcut instanceof MouseShortcut) {
          MouseShortcut mouseShortcut = (MouseShortcut)shortcut;
          Element element = new Element("mouse-shortcut");
          element.setAttribute("keystroke", getMouseShortcutString(mouseShortcut));
          actionElement.addContent(element);
        }else{
          throw new IllegalStateException("unknown shortcut class: " + shortcut);
        }
      }
      keymapElement.addContent(actionElement);
    }
    return keymapElement;
  }

  private boolean areShortcutsEqual(Shortcut[] shortcuts1, Shortcut[] shortcuts2) {
    if(shortcuts1.length != shortcuts2.length) {
      return false;
    }
    for(int j = 0; j < shortcuts1.length; j++){
      Shortcut shortcut = shortcuts1[j];
      Shortcut parentShortcutEqual = null;
      for(int i = 0; i < shortcuts2.length; i++) {
        Shortcut parentShortcut = shortcuts2[i];
        if(shortcut.equals(parentShortcut)) {
          parentShortcutEqual = parentShortcut;
          break;
        }
      }
      if(parentShortcutEqual == null) {
        return false;
      }
    }
    return true;
  }

  /**
   * @return string representation of passed keystroke.
   */
  private static String getKeyShortcutString(KeyStroke keyStroke) {
    StringBuffer buf = new StringBuffer();
    int modifiers = keyStroke.getModifiers();
    if((modifiers & InputEvent.SHIFT_MASK) != 0) {
      buf.append("shift ");
    }
    if((modifiers & InputEvent.CTRL_MASK) != 0) {
      buf.append("control ");
    }
    if((modifiers & InputEvent.META_MASK) != 0) {
      buf.append("meta ");
    }
    if((modifiers & InputEvent.ALT_MASK) != 0) {
      buf.append("alt ");
    }

    buf.append((String)ourNamesForKeycodes.get(new Integer(keyStroke.getKeyCode())));

    return buf.toString();
  }

  /**
   * @return string representation of passed mouse shortcut. This method should
   * be used only for serializing of the <code>MouseShortcut</code>
   */
  private static String getMouseShortcutString(MouseShortcut shortcut){
    StringBuffer buffer=new StringBuffer();

    // modifiers

    int modifiers=shortcut.getModifiers();
    if((MouseEvent.SHIFT_DOWN_MASK&modifiers)>0){
      buffer.append("shift ");
    }
    if((MouseEvent.CTRL_DOWN_MASK&modifiers)>0){
      buffer.append("control ");
    }
    if((MouseEvent.META_DOWN_MASK&modifiers)>0){
      buffer.append("meta ");
    }
    if((MouseEvent.ALT_DOWN_MASK&modifiers)>0){
      buffer.append("alt ");
    }
    if((MouseEvent.ALT_GRAPH_DOWN_MASK&modifiers)>0){
      buffer.append("altGraph ");
    }

    // button

    int button=shortcut.getButton();
    if(MouseEvent.BUTTON1==button){
      buffer.append("button1 ");
    }else if(MouseEvent.BUTTON2==button){
      buffer.append("button2 ");
    }else if(MouseEvent.BUTTON3==button){
      buffer.append("button3 ");
    }else{
      throw new IllegalStateException("unknown button: "+button);
    }

    if(shortcut.getClickCount()>1){
      buffer.append("doubleClick");
    }
    return buffer.toString().trim(); // trim trailing space (if any)
  }

  /**
   * @return IDs of the action which are specified in the keymap. It doesn't
   * return IDs of action from parent keymap.
   */
  private String[] getOwnActionIds() {
    return (String[])myActionId2ListOfShortcuts.keySet().toArray(new String[myActionId2ListOfShortcuts.size()]);
  }

  public String[] getActionIds() {
    ArrayList<String> ids = new ArrayList<String>();
    if (myParent != null) {
      String[] parentIds = getParentActionIds();
      for (int i = 0; i < parentIds.length; i++) {
        String id = parentIds[i];
        ids.add(id);
      }
    }
    String[] ownActionIds = getOwnActionIds();
    for (int i = 0; i < ownActionIds.length; i++) {
      String id = ownActionIds[i];
      if (!ids.contains(id)) {
        ids.add(id);
      }
    }
    return ids.toArray(new String[ids.size()]);
  }

  protected String[] getParentActionIds() {
    return myParent.getActionIds();
  }

  public boolean areMnemonicsEnabled() {
    return !myDisableMnemonics;
  }

  public void setDisableMnemonics(boolean disableMnemonics) {
    myDisableMnemonics = disableMnemonics;
  }

  public HashMap<String, ArrayList<KeyboardShortcut>> getConflicts(String actionId, KeyboardShortcut keyboardShortcut) {
    HashMap<String, ArrayList<KeyboardShortcut>> result = new HashMap<String, ArrayList<KeyboardShortcut>>();

    String[] actionIds = getActionIds(keyboardShortcut.getFirstKeyStroke());
    for(int i = 0; i < actionIds.length; i++) {
      String id = actionIds[i];
      if (id.equals(actionId)){
        continue;
      }

      if (actionId.startsWith("Editor") && id.equals("$" + actionId.substring(6))) {
        continue;
      }
      if (StringUtil.startsWithChar(actionId, '$') && id.equals("Editor" + actionId.substring(1))) {
        continue;
      }

      Shortcut[] shortcuts = getShortcuts(id);
      for (int j = 0; j < shortcuts.length; j++) {
        if (!(shortcuts[j] instanceof KeyboardShortcut)){
          continue;
        }

        KeyboardShortcut shortcut = (KeyboardShortcut)shortcuts[j];

        if (!shortcut.getFirstKeyStroke().equals(keyboardShortcut.getFirstKeyStroke())) {
          continue;
        }

        if (
          keyboardShortcut.getSecondKeyStroke() != null &&
          shortcut.getSecondKeyStroke() != null &&
          !keyboardShortcut.getSecondKeyStroke().equals(shortcut.getSecondKeyStroke())
        ){
          continue;
        }

        ArrayList<KeyboardShortcut> list = result.get(id);
        if (list == null) {
          list = new ArrayList<KeyboardShortcut>();
          result.put(id, list);
        }

        list.add(shortcut);
      }
    }

    return result;
  }

  public void addShortcutChangeListener(Keymap.Listener listener) {
    myListeners.add(listener);
  }

  public void removeShortcutChangeListener(Keymap.Listener listener) {
    myListeners.remove(listener);
  }

  private void fireShortcutChanged(String actionId) {
    Keymap.Listener[] listeners = myListeners.toArray(new Keymap.Listener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      Listener listener = listeners[i];
      listener.onShortcutChanged(actionId);
    }
  }
}