package com.intellij.ui.content;


import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.actionSystem.DataProvider;

import javax.swing.*;
import java.awt.*;

public class ComponentContentUI implements ContentUI {
  private ContentManager myManager;
  private JPanel myPanel;

  public ComponentContentUI() {
    myPanel = new MyPanel();
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void setManager(ContentManager manager) {
    myManager = manager;
    myManager.addContentManagerListener(new MyContentManagerListener());
  }

  private class MyContentManagerListener extends ContentManagerAdapter {
    public void selectionChanged(ContentManagerEvent event) {
      myPanel.removeAll();
      Content content = event.getContent();
      if (content != null) {
        myPanel.add(content.getComponent(), BorderLayout.CENTER);
        myPanel.validate();
        myPanel.repaint();
      }
    }
  }

  private class MyPanel extends JPanel implements DataProvider {
    public MyPanel() {
      super(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }

    public Object getData(String dataId) {
      if (DataConstantsEx.CONTENT_MANAGER.equals(dataId)) {
        return myManager;
      }
      return null;
    }
  }

  public boolean isSingleSelection() {
    return true;
  }

  public boolean isToSelectAddedContent() {
    return true;
  }

  public void dispose() {
  }
}

