/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.diagnostic;

/**
 * @author kir
 */

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.PluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.reporter.ScrData;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class IdeErrorsDialog extends DialogWrapper implements MessagePoolListener {
  private JTextPane myDetailsPane;
  private List myFatalErrors;
  private JList myHeadersList;
  private DefaultListModel myHeadersModel = new DefaultListModel();
  private final MessagePool myMessagePool;

  public IdeErrorsDialog(MessagePool messagePool) {
    super(JOptionPane.getRootFrame(), false);

    myMessagePool = messagePool;
    myHeadersList = new JList(myHeadersModel);

    init();
  }

  public void newEntryAdded() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        rebuildHeaders();
      }
    });
  }

  public void poolCleared() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        doOKAction();
      }
    });
  }

  protected Action[] createActions() {
    return new Action[]{new SubmitBugAction(), new ShutdownAction(), new ClearFatalsAction(), new CloseAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    return myHeadersList;
  }

  protected JComponent createCenterPanel() {
    setTitle("IDE Fatal Errors");

    rebuildHeaders();
    JPanel root = new JPanel(new BorderLayout());

    JPanel headersPanel = new JPanel(new BorderLayout());
    headersPanel.add(new JLabel("Messages:"), BorderLayout.NORTH);
    myHeadersList.setCellRenderer(new CellRenderer());
    headersPanel.add(new JScrollPane(myHeadersList), BorderLayout.CENTER);

    JPanel detailsPanel = new JPanel(new BorderLayout());
    detailsPanel.add(new JLabel("Details:"), BorderLayout.NORTH);
    myDetailsPane = new JTextPane();
    myDetailsPane.setEditable(false);
    detailsPanel.add(new JScrollPane(myDetailsPane), BorderLayout.CENTER);

    Splitter splitter = new Splitter(true, .5f);
    splitter.setFirstComponent(headersPanel);
    splitter.setSecondComponent(detailsPanel);

    root.add(splitter, BorderLayout.CENTER);

    root.setPreferredSize(new Dimension(600, 550));

    myHeadersList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (myHeadersList.isSelectionEmpty() || myHeadersList.getModel().getSize() == 0) {
          hideMessageDetails();
          return;
        }

        showMessageDetails(getMessageAt(myHeadersList.getSelectedIndex()));
      }
    });

    myHeadersList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() % 2 == 0) {
          final AbstractMessage logMessage = getMessageAt(myHeadersList.getSelectedIndex());
          if (logMessage.isSumbitted()) {
            String url = logMessage.getSubmissionInfo().getURL();
            if (url != null) {
              BrowserUtil.launchBrowser(url);
            }
          }
        }
      }
    });

    moveSelectionToEarliestMessage();
    return root;
  }

  private AbstractMessage getMessageAt(int aIndex) {
    return (AbstractMessage)((ArrayList)myHeadersList.getModel().getElementAt(aIndex)).get(0);
  }

  private void moveSelectionToEarliestMessage() {
    for (int i = myHeadersList.getModel().getSize() - 1; i > 0; i--) {
      final AbstractMessage each = getMessageAt(i);
      if (!each.isRead()) {
        myHeadersList.setSelectedIndex(i);
        break;
      }
    }

    if (myHeadersList.isSelectionEmpty() && myHeadersList.getModel().getSize() > 0) {
      myHeadersList.setSelectedIndex(0);
    }
  }

  private void rebuildHeaders() {

    final int selectedIndex = myHeadersList.getSelectedIndex();

    myHeadersModel.removeAllElements();
    myFatalErrors = myMessagePool.getFatalErrors(true, true);
    Collections.reverse(myFatalErrors);

    Map<String, ArrayList<AbstractMessage>> hash2Messages = buildHashcode2MessageListMap(myFatalErrors);

    final Iterator<ArrayList<AbstractMessage>> messageLists = hash2Messages.values().iterator();
    while (messageLists.hasNext()) {
      myHeadersModel.addElement(messageLists.next());
    }

    if (selectedIndex < myHeadersModel.getSize()) {
      myHeadersList.setSelectedIndex(selectedIndex);
    }
  }

  private Map<String, ArrayList<AbstractMessage>> buildHashcode2MessageListMap(List aErrors) {
    Map<String, ArrayList<AbstractMessage>> hash2Messages = new LinkedHashMap<String, ArrayList<AbstractMessage>>();
    for (int i = 0; i < aErrors.size(); i++) {
      final AbstractMessage each = (AbstractMessage)aErrors.get(i);
      final String hashcode = ScrData.getThrowableHashCode(each.getThrowable());
      ArrayList<AbstractMessage> list;
      if (hash2Messages.containsKey(hashcode)) {
        list = hash2Messages.get(hashcode);
      }
      else {
        list = new ArrayList<AbstractMessage>();
        hash2Messages.put(hashcode, list);
      }
      list.add(0, each);
    }
    return hash2Messages;
  }

  private void showMessageDetails(AbstractMessage aMessage) {
    myDetailsPane.setText(aMessage.getThrowableText());
    myDetailsPane.setCaretPosition(0);
  }

  private void hideMessageDetails() {
    myDetailsPane.setText("");
  }

  public static String findPluginName(Throwable t) {
    StackTraceElement[] elements = t.getStackTrace();
    for (int i = 0; i < elements.length; i++) {
      StackTraceElement element = elements[i];
      String className = element.getClassName();
      if (PluginManager.isPluginClass(className) &&
          !className.startsWith("com.intellij") &&
          !className.startsWith("org.jetbrains")) {
        return PluginManager.getPluginByClassName(className);
      }
    }

    if (t instanceof NoSuchMethodException) {
      // check is method called from plugin classes
      if (t.getMessage() != null) {
        String className = "";
        StringTokenizer tok = new StringTokenizer(t.getMessage(), ".");
        while (tok.hasMoreTokens()) {
          String token = tok.nextToken();
          if (token.length() > 0 && Character.isJavaIdentifierStart(token.charAt(0))) {
            className += token;
          }
        }

        if (PluginManager.isPluginClass(className)) {
          return PluginManager.getPluginByClassName(className);
        }
      }
    }
    else if (t instanceof ClassNotFoundException) {
      // check is class from plugin classes
      if (t.getMessage() != null) {
        String className = t.getMessage();

        if (PluginManager.isPluginClass(className)) {
          return PluginManager.getPluginByClassName(className);
        }
      }
    }
    else if (t instanceof PluginException) {
      return ((PluginException)t).getDescriptor().getName();
    }

    return null;
  }

  private static class CellRenderer extends DefaultListCellRenderer {
    private SimpleColoredComponent myComponent = new SimpleColoredComponent();

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      ArrayList<AbstractMessage> messageList = (ArrayList<AbstractMessage>)value;
      AbstractMessage message = messageList.get(0);
      String prefix = messageList.size() > 1 ? "(" + messageList.size() + ") " : "";

      myComponent.clear();
      final Color unimportantColor = new Color(102, 102, 102);
      final String text = message.getMessage();

      final SimpleTextAttributes grayed = new SimpleTextAttributes(Font.PLAIN, unimportantColor);
      final SimpleTextAttributes blue = new SimpleTextAttributes(Font.PLAIN, Color.blue);

      if (!message.isRead() && !message.isSumbitted()) {
        myComponent.append(prefix + text, new SimpleTextAttributes(Font.BOLD, Color.black));
      }
      else if (message.isSumbitted()) {
        myComponent.append(prefix, grayed);

        myComponent.append("Submitted: #" + message.getSubmissionInfo().getLinkText(), blue);
        myComponent.append(":" + text, blue);
        myComponent.append(" (double-click to open in browser)", grayed);
      }
      else if (message.isRead()) {
        myComponent.append(prefix + text, grayed);
      }

      myComponent.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
      return myComponent;
    }
  }

  private class ShutdownAction extends AbstractAction {
    public ShutdownAction() {
      super("Shut_down");
    }

    public void actionPerformed(ActionEvent e) {
      myMessagePool.setJvmIsShuttingDown();
      ApplicationManager.getApplication().exit();
    }
  }

  private class ClearFatalsAction extends AbstractAction {
    public ClearFatalsAction() {
      super("_Clear And Close");
    }

    public void actionPerformed(ActionEvent e) {
      myMessagePool.clearFatals();
      doOKAction();
    }
  }

  private class SubmitBugAction extends AbstractAction {
    public SubmitBugAction() {
      super("_Report To JetBrains");
      putValue(DEFAULT, Boolean.TRUE);
    }

    public void actionPerformed(ActionEvent e) {
      if (myHeadersList.isSelectionEmpty()) {
        Messages.showMessageDialog("Please first select error to report", "Cannot Report", Messages.getInformationIcon());
        myHeadersList.requestFocus();
        return;
      }

      final int selectedIndex = myHeadersList.getSelectedIndex();
      final AbstractMessage logMessage = getMessageAt(selectedIndex);

      if (logMessage.isSumbitted()) {
        //        Messages.showMessageDialog("This error was already submitted: #" + logMessage.getScrID(), "Already Submitted", Messages.getInformationIcon());
        Messages.showMessageDialog("This error was already submitted", "Already Submitted", Messages.getInformationIcon());
        return;
      }

      reportMessage(logMessage);
      rebuildHeaders();
      myHeadersList.setSelectedIndex(selectedIndex);

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myHeadersList.requestFocus();
        }
      });
    }

    private void reportMessage(final AbstractMessage logMessage) {
      final String pluginName = findPluginName(logMessage.getThrowable());
      final Object[] reporters = Extensions.getRootArea().getExtensionPoint(PluginManager.ERROR_HANDLER_EXTENSION_POINT).getExtensions();
      ErrorReportSubmitter submitter = null;
      for (int i = 0; i < reporters.length; i++) {
        ErrorHandlerExtension reporterBean = (ErrorHandlerExtension)reporters[i];
        if (Comparing.equal(pluginName, reporterBean.getPluginName())) {
          final PluginDescriptor plugin = PluginManager.getPlugin(pluginName);
          final ClassLoader loader;
          if (plugin != null) {
            loader = plugin.getLoader();
          }
          else {
            loader = getClass().getClassLoader();
          }

          try {
            final Class submitterClass = Class.forName(reporterBean.getHandlerClass(), true, loader);
            submitter = (ErrorReportSubmitter)submitterClass.newInstance();
          }
          catch (Exception e) {
            break;
          }
        }
      }

      if (submitter != null) {
        logMessage.setSubmitted(submitter.submit(getEvents(logMessage), getContentPane()));
      }
    }

    private IdeaLoggingEvent[] getEvents(final AbstractMessage logMessage) {
      if (logMessage instanceof GroupedLogMessage) {
        final List<AbstractMessage> messages = ((GroupedLogMessage)logMessage).getMessages();
        IdeaLoggingEvent[] res = new IdeaLoggingEvent[messages.size()];
        for (int i = 0; i < res.length; i++) {
          res[i] = getEvent(messages.get(i));
        }
        return res;
      }
      return new IdeaLoggingEvent[]{getEvent(logMessage)};
    }

    private IdeaLoggingEvent getEvent(final AbstractMessage logMessage) {
      return new IdeaLoggingEvent(logMessage.getMessage(), logMessage.getThrowable());
    }
  }

  protected void doOKAction() {
    markAllAsRead();
    super.doOKAction();
  }

  private void markAllAsRead() {
    for (int i = 0; i < myFatalErrors.size(); i++) {
      AbstractMessage each = (AbstractMessage)myFatalErrors.get(i);
      each.setRead(true);
    }
  }

  public void doCancelAction() {
    markAllAsRead();
    super.doCancelAction();
  }

  protected class CloseAction extends AbstractAction {
    public CloseAction() {
      putValue(Action.NAME, "C_lose");
    }

    public void actionPerformed(ActionEvent e) {
      doOKAction();
    }
  }

  protected String getDimensionServiceKey() {
    return "IdeErrosDialog";
  }

}
