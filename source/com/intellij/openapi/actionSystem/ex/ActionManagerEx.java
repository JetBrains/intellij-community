package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import org.jdom.Element;

import javax.swing.*;
import java.util.Comparator;

public abstract class ActionManagerEx extends ActionManager{
  public static ActionManagerEx getInstanceEx(){
    return (ActionManagerEx)getInstance();
  }
  
  public abstract void addTimerListener(int delay, TimerListener listener);
  public abstract void removeTimerListener(TimerListener listener);

  /**
   * @return all action <code>id</code>s which have the specified prefix.
   */
  public abstract String[] getActionIds(String idPrefix);

  /**
   * @param element XML element for <code>actions</code> tag.
   * @param loader
   */
  public abstract void processActionsElement(Element element, ClassLoader loader);

  public abstract void addAnActionListener(AnActionListener listener);
  public abstract void removeAnActionListener(AnActionListener listener);
  public abstract void fireBeforeActionPerformed(AnAction action, DataContext dataContext);
  public abstract void fireBeforeEditorTyping(char c, DataContext dataContext);
  /**
   * For logging purposes
   */
  public abstract String getLastPreformedActionId();

  /**
   * Comparator compares action ids (String) on order of action registration.
   * @return a negative integer if action that corresponds to the first id was registered earler than the action that corresponds
   *  to the second id; zero if both ids are equal; a positive number otherwise.
   */
  public abstract Comparator getRegistrationOrderComparator();

  public abstract String[] getConfigurableGroups();

  /**
   * Similar to {@link javax.swing.KeyStroke#getKeyStroke(String)} but allows keys in lower case.
   * I.e. "control x" is accepted and interpreted as "control X".
   * @return null if string cannot be parsed.
   */
  public static KeyStroke getKeyStroke(String s) {
    KeyStroke result = null;
    try {
      result = KeyStroke.getKeyStroke(s);
    } catch (Exception ex) {
    }

    if (result == null && s != null && s.length() >= 2 && s.charAt(s.length() - 2) == ' ') {
      try {
        String s1 = s.substring(0, s.length() - 1) + Character.toUpperCase(s.charAt(s.length() - 1));
        result = KeyStroke.getKeyStroke(s1);
      } catch (Exception ex) {
      }
    }

    return result;
  }
}
