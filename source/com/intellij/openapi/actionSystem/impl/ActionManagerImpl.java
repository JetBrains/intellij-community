package com.intellij.openapi.actionSystem.impl;

import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.ex.TimerListener;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ide.DataManager;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public final class ActionManagerImpl extends ActionManagerEx implements JDOMExternalizable, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.impl.ActionManagerImpl");
  private final Object myLock = new Object();
  private THashMap myId2Action;
  private THashMap myId2Index;
  private THashMap myAction2Id;
  private ArrayList myNotRegisteredInternalActionIds;
  private final Map<TimerListener, Timer> myRunnable2Timer = new HashMap<TimerListener, Timer>();
  private int myRegisteredActionsCount;
  private ArrayList myActionListeners;
  private AnActionListener[] myCachedActionListeners;
  private String myLastPreformedActionId;
  private KeymapManager myKeymapManager;
  private DataManager myDataManager;

  ActionManagerImpl(KeymapManager keymapManager, DataManager dataManager) {
    myId2Action = new THashMap();
    myId2Index = new THashMap();
    myAction2Id = new THashMap();
    myNotRegisteredInternalActionIds = new ArrayList();
    myActionListeners = new ArrayList();
    myCachedActionListeners = null;
    myKeymapManager = keymapManager;
    myDataManager = dataManager;
  }

  public void initComponent() {}

  public void disposeComponent() {}

  public void addTimerListener(int delay, final TimerListener listener) {
    Timer timer = new Timer(delay,
                            new ActionListener() {
                              public void actionPerformed(ActionEvent e) {
                                ModalityState modalityState = listener.getModalityState();
                                if (modalityState == null) return;
                                if (!ModalityState.current().dominates(modalityState)) {
                                  listener.run();
                                }
                              }
                            });
    synchronized (myRunnable2Timer) {
      myRunnable2Timer.put(listener, timer);
    }
    timer.setRepeats(true);
    timer.start();
  }

  public void removeTimerListener(TimerListener listener) {
    synchronized (myRunnable2Timer) {
      Timer timer = myRunnable2Timer.get(listener);
      if (timer != null) {
        timer.stop();
        myRunnable2Timer.remove(listener);
      }
      else {
        LOG.error("removeTimerListener for not registered");
      }
    }
  }

  public ActionPopupMenu createActionPopupMenu(String place, ActionGroup group) {
    return new ActionPopupMenuImpl(place, group);
  }

  public ActionToolbar createActionToolbar(final String place, final ActionGroup group, final boolean horizontal) {
    return new ActionToolbarImpl(place, group, horizontal, myDataManager, this, (KeymapManagerEx)myKeymapManager);
  }


  public void readExternal(Element element) {
    final ClassLoader classLoader = this.getClass().getClassLoader();
    for (Iterator i = element.getChildren().iterator(); i.hasNext();) {
      Element children = (Element)i.next();
      if ("actions".equals(children.getName())) {
        processActionsElement(children, classLoader);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }

  public AnAction getAction(String id) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: getAction(" + id + ")");
    }
    if (id == null) {
      throw new IllegalArgumentException("id cannot be null");
    }
    return getActionImpl(id, false);
  }

  private AnAction getActionImpl(String id, boolean canReturnStub) {
    AnAction action = (AnAction)myId2Action.get(id);
    if (!canReturnStub && (action instanceof ActionStub)) {
      action = convert((ActionStub)action);
    }
    return action;
  }

  /**
   * Converts action's stub to normal action.
   */
  private AnAction convert(ActionStub stub) {
    synchronized (myLock) {
      LOG.assertTrue(myAction2Id.contains(stub));
      myAction2Id.remove(stub);

      LOG.assertTrue(myId2Action.contains(stub.getId()));

      AnAction action = (AnAction)myId2Action.remove(stub.getId());
      LOG.assertTrue(action != null);
      LOG.assertTrue(action.equals(stub));

      Object obj;
      String className = stub.getClassName();
      try {
        obj = Class.forName(className, true, stub.getLoader()).newInstance();
      }
      catch (ClassNotFoundException e) {
        throw new IllegalStateException("class with name \"" + className + "\" not found");
      }
      catch (Exception e) {
        throw new IllegalStateException("cannot create class \"" + className + "\"");
      }

      if (!(obj instanceof AnAction)) {
        throw new IllegalStateException("class with name \"" + className + "\" should be instance of " + AnAction.class.getName());
      }

      stub.initAction((AnAction)obj);
      ((AnAction)obj).getTemplatePresentation().setText(stub.getText());

      myId2Action.put(stub.getId(), obj);
      myAction2Id.put(obj, stub.getId());

      return (AnAction)obj;
    }
  }


  public String getId(AnAction action) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: getId(" + action + ")");
    }
    if (action == null) {
      throw new IllegalArgumentException("action cannot be null");
    }
    LOG.assertTrue(!(action instanceof ActionStub));
    synchronized (myLock) {
      return (String)myAction2Id.get(action);
    }
  }

  public String[] getActionIds(String idPrefix) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: getActionIds(" + idPrefix + ")");
    }
    if (idPrefix == null) {
      LOG.error("idPrefix cannot be null");
      return null;
    }
    synchronized (myLock) {
      ArrayList idList = new ArrayList();
      for (Iterator i = myId2Action.keySet().iterator(); i.hasNext();) {
        String id = (String)i.next();
        if (id.startsWith(idPrefix)) {
          idList.add(id);
        }
      }
      return (String[])idList.toArray(new String[idList.size()]);
    }
  }

  /**
   * @return instance of ActionGroup or ActionStub. The method never returns real subclasses
   *         of <code>AnAction</code>.
   */
  private AnAction processActionElement(Element element, final ClassLoader loader) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processActionElement(" + element.getName() + ")");
    }
    if (!"action".equals(element.getName())) {
      LOG.error("unexpected name of element \"" + element.getName() + "\"");
      return null;
    }
    String className = element.getAttributeValue("class");
    if (className == null || className.length() == 0) {
      LOG.error("action element should have specified \"class\" attribute");
      return null;
    }
    // read ID and register loaded action
    String id = element.getAttributeValue("id");
    if (id == null || id.length() == 0) {
      LOG.error("ID of the action cannot be an empty string");
      return null;
    }
    if (Boolean.valueOf(element.getAttributeValue("internal")).booleanValue() && !ApplicationManagerEx.getApplicationEx().isInternal()) {
      myNotRegisteredInternalActionIds.add(id);
      return null;
    }

    // text
    String text = element.getAttributeValue("text");
    if (text == null) {
      LOG.error("'text' attribute is mandatory (action ID=" + id + ")");
      return null;
    }
    ActionStub stub = new ActionStub(className, id, text, loader);
    Presentation presentation = stub.getTemplatePresentation();
    presentation.setText(text);
    // description
    String description = element.getAttributeValue("description");
    presentation.setDescription(description);
    // icon
    String iconPath = element.getAttributeValue("icon");
    if (iconPath != null) {
      try {
        final Class actionClass = Class.forName(className, true, loader);
        presentation.setIcon(IconLoader.getIcon(iconPath, actionClass));
      }
      catch (ClassNotFoundException ignored) {
      }
    }
    // process all links and key bindings if any
    for (Iterator i = element.getChildren().iterator(); i.hasNext();) {
      Element e = (Element)i.next();
      if ("add-to-group".equals(e.getName())) {
        processAddToGroupNode(stub, e);
      }
      else if ("keyboard-shortcut".equals(e.getName())) {
        processKeyboardShortcutNode(e, id);
      }
      else if ("mouse-shortcut".equals(e.getName())) {
        processMouseShortcutNode(e, id);
      }
      else {
        LOG.error("unexpected name of element \"" + e.getName() + "\"");
        return null;
      }
    }
    // register action
    registerAction(id, stub);
    return stub;
  }

  private AnAction processGroupElement(Element element, final ClassLoader loader) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processGroupElement(" + element.getName() + ")");
    }
    if (!"group".equals(element.getName())) {
      LOG.error("unexpected name of element \"" + element.getName() + "\"");
      return null;
    }
    String className = element.getAttributeValue("class");
    if (className == null) { // use default group if class isn't specified
      className = DefaultActionGroup.class.getName();
    }
    try {
      Class aClass = Class.forName(className, true, loader);
      Object obj = new ConstructorInjectionComponentAdapter(className, aClass).getComponentInstance(ApplicationManager.getApplication().getPicoContainer());

      if (!(obj instanceof ActionGroup)) {
        LOG.error("class with name \"" + className + "\" should be instance of " + ActionGroup.class.getName());
        return null;
      }
      if (element.getChildren().size() > 0) {
        if (!(obj instanceof DefaultActionGroup)) {
          LOG.error("class with name \"" + className + "\" should be instance of " + DefaultActionGroup.class.getName() +
                    " because there are children specified");
          return null;
        }
      }
      ActionGroup group = (ActionGroup)obj;
      // read ID and register loaded group
      String id = element.getAttributeValue("id");
      if (id != null && id.length() == 0) {
        LOG.error("ID of the group cannot be an empty string");
        return null;
      }
      if (Boolean.valueOf(element.getAttributeValue("internal")).booleanValue() && !ApplicationManagerEx.getApplicationEx().isInternal()) {
        myNotRegisteredInternalActionIds.add(id);
        return null;
      }

      if (id != null) {
        registerAction(id, group);
      }
      // text
      Presentation presentation = group.getTemplatePresentation();
      String text = element.getAttributeValue("text");
      presentation.setText(text);
      // description
      String description = element.getAttributeValue("description");
      presentation.setDescription(description);
      // icon
      String iconPath = element.getAttributeValue("icon");
      if (iconPath != null) {
        presentation.setIcon(IconLoader.getIcon(iconPath));
      }
      // popup
      String popup = element.getAttributeValue("popup");
      if (popup != null) {
        group.setPopup(Boolean.valueOf(popup).booleanValue());
      }
      // process all group's children. There are other groups, actions, references and links
      for (Iterator i = element.getChildren().iterator(); i.hasNext();) {
        Element child = (Element)i.next();
        String name = child.getName();
        if ("action".equals(name)) {
          AnAction action = processActionElement(child, loader);
          if (action != null) {
            LOG.assertTrue((action instanceof ActionGroup) || (action instanceof ActionStub));
            ((DefaultActionGroup)group).add(action);
          }
        }
        else if ("separator".equals(name)) {
          processSeparatorNode((DefaultActionGroup)group, child);
        }
        else if ("group".equals(name)) {
          AnAction action = processGroupElement(child, loader);
          if (action != null) {
            ((DefaultActionGroup)group).add(action);
          }
        }
        else if ("add-to-group".equals(name)) {
          processAddToGroupNode(group, child);
        }
        else if ("reference".equals(name)) {
          AnAction action = processReferenceElement(child);
          if (action != null) {
            ((DefaultActionGroup)group).add(action);
          }
        }
        else {
          LOG.error("unexpected name of element \"" + name + "\n");
          return null;
        }
      }
      return group;
    }
    catch (ClassNotFoundException e) {
      LOG.error("class with name \"" + className + "\" not found");
      return null;
    }
    catch (Exception e) {
      LOG.error("cannot create class \"" + className + "\"", e);
      return null;
    }
  }

  /**
   * @param element description of link
   */
  private void processAddToGroupNode(AnAction action, Element element) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processAddToGroupNode(" + action + "," + element.getName() + ")");
    }

    // Real subclasses of AnAction should not be here
    LOG.assertTrue((action instanceof ActionGroup) || (action instanceof ActionStub) || (action instanceof Separator));

    if (!"add-to-group".equals(element.getName())) {
      LOG.error("unexpected name of element \"" + element.getName() + "\"");
      return;
    }
    String groupId = element.getAttributeValue("group-id");
    if (groupId == null || groupId.length() == 0) {
      LOG.error("attribute \"group-id\" should be defined");
      return;
    }
    AnAction parentGroup = getActionImpl(groupId, true);
    if (parentGroup == null) {
      LOG.error("action with id \"" + groupId + "\" isn't registered; action will be added to the \"Other\" group");
      parentGroup = getActionImpl(IdeActions.GROUP_OTHER_MENU, true);
    }
    if (!(parentGroup instanceof DefaultActionGroup)) {
      LOG.error("action with id \"" + groupId + "\" should be instance of " + DefaultActionGroup.class.getName());
      return;
    }
    String anchorStr = element.getAttributeValue("anchor");
    if (anchorStr == null || groupId.length() == 0) {
      LOG.error("attribute \"anchor\" should be defined");
      return;
    }
    Anchor anchor;
    if ("first".equalsIgnoreCase(anchorStr)) {
      anchor = Anchor.FIRST;
    }
    else if ("last".equalsIgnoreCase(anchorStr)) {
      anchor = Anchor.LAST;
    }
    else if ("before".equalsIgnoreCase(anchorStr)) {
      anchor = Anchor.BEFORE;
    }
    else if ("after".equalsIgnoreCase(anchorStr)) {
      anchor = Anchor.AFTER;
    }
    else {
      LOG.error("anchor should be one of the following constants: \"first\", \"last\", \"before\" or \"after\"");
      return;
    }
    String relativeToActionId = element.getAttributeValue("relative-to-action");
    if ((Anchor.BEFORE == anchor || Anchor.AFTER == anchor) && relativeToActionId == null) {
      LOG.error("\"relative-to-action\" cannot be null if anchor is \"after\" or \"before\"");
      return;
    }
    ((DefaultActionGroup)parentGroup).add(action, new Constraints(anchor, relativeToActionId));
  }

  /**
   * @param parentGroup group wich is the parent of the separator. It can be <code>null</code> in that
   *                    case separator will be added to group described in the <add-to-group ....> subelement.
   * @param element     XML element which represent separator.
   */
  private void processSeparatorNode(DefaultActionGroup parentGroup, Element element) {
    if (!"separator".equals(element.getName())) {
      LOG.error("unexpected name of element \"" + element.getName() + "\"");
      return;
    }
    Separator separator = Separator.getInstance();
    if (parentGroup != null) {
      parentGroup.add(separator);
    }
    // try to find inner <add-to-parent...> tag
    for (Iterator i = element.getChildren().iterator(); i.hasNext();) {
      Element child = (Element)i.next();
      if ("add-to-group".equals(child.getName())) {
        processAddToGroupNode(separator, child);
      }
    }
  }

  private void processKeyboardShortcutNode(Element element, String actionId) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processKeyboardShortcutNode(" + element.getName() + ")");
    }

    String firstStrokeString = element.getAttributeValue("first-keystroke");
    if (firstStrokeString == null) {
      LOG.error("\"first-keystroke\" attribute must be specified for action with id=" + actionId);
      return;
    }
    KeyStroke firstKeyStroke = getKeyStroke(firstStrokeString);
    if (firstKeyStroke == null) {
      LOG.error("\"first-keystroke\" attribute has invalid value for action with id=" + actionId);
      return;
    }

    KeyStroke secondKeyStroke = null;
    String secondStrokeString = element.getAttributeValue("second-keystroke");
    if (secondStrokeString != null) {
      secondKeyStroke = getKeyStroke(secondStrokeString);
      if (secondKeyStroke == null) {
        LOG.error("\"second-keystroke\" attribute has invalid value for action with id=" + actionId);
        return;
      }
    }

    String keymapName = element.getAttributeValue("keymap");
    if (keymapName == null || keymapName.trim().length() == 0) {
      LOG.error("attribute \"keymap\" should be defined");
      return;
    }
    Keymap keymap = myKeymapManager.getKeymap(keymapName);
    if (keymap == null) {
      LOG.error("keymap \"" + keymapName + "\" not found");
      return;
    }

    keymap.addShortcut(actionId, new KeyboardShortcut(firstKeyStroke, secondKeyStroke));
  }

  private void processMouseShortcutNode(Element element, String actionId) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processMouseShortcutNode(" + element.getName() + ")");
    }

    String keystrokeString = element.getAttributeValue("keystroke");
    if (keystrokeString == null || keystrokeString.trim().length() == 0) {
      LOG.error("\"keystroke\" attribute must be specified for action with id=" + actionId);
      return;
    }
    MouseShortcut shortcut;
    try {
      shortcut = KeymapUtil.parseMouseShortcut(keystrokeString);
    }
    catch (Exception ex) {
      LOG.error("\"keystroke\" attribute has invalid value for action with id=" + actionId);
      return;
    }

    String keymapName = element.getAttributeValue("keymap");
    if (keymapName == null || keymapName.length() == 0) {
      LOG.error("attribute \"keymap\" should be defined");
      return;
    }
    Keymap keymap = KeymapManager.getInstance().getKeymap(keymapName);
    if (keymap == null) {
      LOG.error("keymap \"" + keymapName + "\" not found");
      return;
    }

    keymap.addShortcut(actionId, shortcut);
  }

  private AnAction processReferenceElement(Element element) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processReferenceElement(" + element.getName() + ")");
    }
    if (!"reference".equals(element.getName())) {
      LOG.error("unexpected name of element \"" + element.getName() + "\"");
      return null;
    }
    String ref = element.getAttributeValue("ref");

    if (ref==null) {
      // support old style references by id
      ref = element.getAttributeValue("id");
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("ref=\"" + ref + "\"");
    }
    if (ref == null || ref.length() == 0) {
      LOG.error("ID of reference element should be defined");
      return null;
    }

    AnAction action = getActionImpl(ref, true);

    if (action == null) {
      if (!myNotRegisteredInternalActionIds.contains(ref)) {
        LOG.error("action specified by reference isn't registered (ID=" + ref + ")");
      }
      return null;
    }
    LOG.assertTrue((action instanceof ActionGroup) || (action instanceof ActionStub));
    return action;
  }

  public void processActionsElement(Element element, ClassLoader loader) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: processActionsNode(" + element.getName() + ")");
    }
    if (!"actions".equals(element.getName())) {
      LOG.error("unexpected name of element \"" + element.getName() + "\"");
      return;
    }
    synchronized (myLock) {
      for (Iterator i = element.getChildren().iterator(); i.hasNext();) {
        Element child = (Element)i.next();
        String name = child.getName();
        if ("action".equals(name)) {
          AnAction action = processActionElement(child, loader);
          if (action != null) {
            LOG.assertTrue((action instanceof ActionGroup) || (action instanceof ActionStub));
          }
        }
        else if ("group".equals(name)) {
          processGroupElement(child, loader);
        }
        else if ("separator".equals(name)) {
          processSeparatorNode(null, child);
        }
        else {
          LOG.error("unexpected name of element \"" + name + "\n");
        }
      }
    }
  }

  public void registerAction(String actionId, AnAction action) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: registerAction(" + action + ")");
    }
    if (action == null) {
      LOG.error("action cannot be null");
      return;
    }
    if (actionId == null) {
      LOG.error("action's id cannot be null");
      return;
    }
    synchronized (myLock) {
      if (myId2Action.containsKey(actionId)) {
        LOG.error("action with the ID \"" + actionId + "\" was already registered. Registered action is " + myId2Action.get(actionId));
        return;
      }
      if (myAction2Id.containsKey(action)) {
        LOG.error("action was already registered for another ID. ID is " + myAction2Id.get(action));
        return;
      }
      myId2Action.put(actionId, action);
      myId2Index.put(actionId, new Integer(myRegisteredActionsCount++));
      myAction2Id.put(action, actionId);

      action.registerCustomShortcutSet(new ProxyShortcutSet(actionId, myKeymapManager), null);
    }
  }

  public void unregisterAction(String actionId) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: unregisterAction(" + actionId + ")");
    }
    if (actionId == null) {
      LOG.error("id cannot be null");
      return;
    }
    synchronized (myLock) {
      if (!myId2Action.containsKey(actionId)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("action with ID " + actionId + " wasn't registered");
          return;
        }
      }
      AnAction oldValue = (AnAction)myId2Action.remove(actionId);
      myAction2Id.remove(oldValue);
      myId2Index.remove(actionId);
    }
  }

  public String getComponentName() {
    return "ActionManager";
  }

  public Comparator getRegistrationOrderComparator() {
    return new Comparator() {
      public int compare(Object o1, Object o2) {
        String id1 = (String)o1;
        String id2 = (String)o2;

        Integer index1Obj = (Integer)myId2Index.get(id1);
        int index1 = index1Obj != null ? index1Obj.intValue() : -1;

        Integer index2Obj = (Integer)myId2Index.get(id2);
        int index2 = index2Obj != null ? index2Obj.intValue() : -1;

        return index1 - index2;
      }
    };
  }

  public String[] getConfigurableGroups() {
    return new String[]{IdeActions.GROUP_MAIN_TOOLBAR, IdeActions.GROUP_EDITOR_POPUP};
  }

  private AnActionListener[] getActionListeners() {
    if (myCachedActionListeners == null) {
      myCachedActionListeners = (AnActionListener[])myActionListeners.toArray(new AnActionListener[myActionListeners.size()]);
    }

    return myCachedActionListeners;
  }

  public void addAnActionListener(AnActionListener listener) {
    myActionListeners.add(listener);
    myCachedActionListeners = null;
  }

  public void removeAnActionListener(AnActionListener listener) {
    myActionListeners.remove(listener);
    myCachedActionListeners = null;
  }

  public void fireBeforeActionPerformed(AnAction action, DataContext dataContext) {
    if (action != null) {
      myLastPreformedActionId = getId(action);
      IdeaLogger.ourLastActionId = myLastPreformedActionId;
    }
    AnActionListener[] listeners = getActionListeners();
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].beforeActionPerformed(action, dataContext);
    }
  }

  public void fireBeforeEditorTyping(char c, DataContext dataContext) {
    AnActionListener[] listeners = getActionListeners();
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].beforeEditorTyping(c, dataContext);
    }
  }

  public String getLastPreformedActionId() {
    return myLastPreformedActionId;
  }
}