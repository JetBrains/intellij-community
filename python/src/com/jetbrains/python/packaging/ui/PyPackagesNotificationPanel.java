package com.jetbrains.python.packaging.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.packaging.PyPIPackageUtil;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class PyPackagesNotificationPanel {
  private final JEditorPane myEditorPane = new JEditorPane();
  private final Project myProject;
  private final Map<String, Runnable> myLinkHandlers = new HashMap<String, Runnable>();
  private String myErrorTitle;
  private String myErrorDescription;

  public PyPackagesNotificationPanel(Project project) {
    myProject = project;
    myEditorPane.setBackground(UIManager.getColor("ArrowButton.background"));
    myEditorPane.setContentType("text/html");
    myEditorPane.setEditable(false);
    myEditorPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final Runnable handler = myLinkHandlers.get(e.getDescription());
        if (handler != null) {
          handler.run();
        }
        else if (myErrorTitle != null && myErrorDescription != null) {
          PyPIPackageUtil.showError(myProject, myErrorTitle, myErrorDescription);
        }
      }
    });
  }

  public void addLinkHandler(String key, Runnable handler) {
    myLinkHandlers.put(key, handler);
  }

  public JComponent getComponent() {
    return myEditorPane;
  }

  public void showSuccess(String text) {
    showContent(text, MessageType.INFO.getPopupBackground());
  }

  private void showContent(String text, final Color background) {
    myEditorPane.removeAll();
    myEditorPane.setText(UIUtil.toHtml(text));
    myEditorPane.setBackground(background);
    myEditorPane.setVisible(true);
    myErrorTitle = null;
    myErrorDescription = null;
  }

  public void showError(String text, final String detailsTitle, final String detailsDescription) {
    showContent(text, MessageType.ERROR.getPopupBackground());
    myErrorTitle = detailsTitle;
    myErrorDescription = detailsDescription;
  }

  public void showWarning(String text) {
    showContent(text, MessageType.WARNING.getPopupBackground());
  }

  public void hide() {
    myEditorPane.setVisible(false);
  }

  public boolean hasLinkHandler(String key) {
    return myLinkHandlers.containsKey(key);
  }
}
