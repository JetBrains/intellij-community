package com.intellij.execution.testframework.sm;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Roman Chernyatchik
 */
public class SMRunnerUtil {
  private static final Logger LOG = Logger.getInstance(SMRunnerUtil.class.getName());

  private SMRunnerUtil() {
  }

  /**
   * Adds runnable to Event Dispatch Queue
   * if we aren't in UnitTest of Headless environment mode
   * @param runnable Runnable
   */
  public static void addToInvokeLater(final Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
    if (application.isHeadlessEnvironment() || application.isUnitTestMode()) {
      runnable.run();
    } else {
      SwingUtilities.invokeLater(runnable);
    }
  }

  public static void registerAsAction(final KeyStroke keyStroke,
                                      final String actionKey,
                                      final Runnable action, final JComponent component) {
    final InputMap inputMap = component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    inputMap.put(keyStroke, actionKey);
    component.getActionMap().put(inputMap.get(keyStroke), new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        action.run();
      }
    });
  }

  public static void runInEventDispatchThread(final Runnable runnable, final ModalityState state) {
    try {
      if (SwingUtilities.isEventDispatchThread()) {
        runnable.run();
      }
      else {
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          public void run() {
            runnable.run();
          }
        }, state);
      }
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

}
