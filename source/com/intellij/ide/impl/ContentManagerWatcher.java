package com.intellij.ide.impl;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ContentManagerWatcher {
  private final ToolWindow myToolWindow;
  private final ContentManager myContentManager;
  private final PropertyChangeListener myPropertyChangeListener;

  public ContentManagerWatcher(ToolWindow toolWindow,ContentManager contentManager) {
    myToolWindow = toolWindow;
    myContentManager = contentManager;
    myToolWindow.setAvailable(contentManager.getContentCount()>0,null);

    myPropertyChangeListener = new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent e) {
        if (Content.PROP_DISPLAY_NAME.equals(e.getPropertyName())) {
          updateTitle();
        }
      }
    };

    contentManager.addContentManagerListener(
      new ContentManagerAdapter(){
        public void selectionChanged(ContentManagerEvent e) {
          updateTitle();
        }

        public void contentAdded(ContentManagerEvent e) {
          e.getContent().addPropertyChangeListener(myPropertyChangeListener);
          myToolWindow.setAvailable(true,null);
        }

        public void contentRemoved(ContentManagerEvent e) {
          e.getContent().removePropertyChangeListener(myPropertyChangeListener);
          myToolWindow.setAvailable(myContentManager.getContentCount()>0,null);
        }
      }
    );

    // Synchonize title with current state of manager

    for(int i=0;i<myContentManager.getContentCount();i++){
      Content content=myContentManager.getContent(i);
      content.addPropertyChangeListener(myPropertyChangeListener);
    }

    updateTitle();
  }

  private void updateTitle() {
    Content content = myContentManager.getSelectedContent();
    if (content != null){
      myToolWindow.setTitle(content.getToolwindowTitle());
    }
  }
}