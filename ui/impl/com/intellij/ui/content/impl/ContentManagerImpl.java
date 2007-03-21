package com.intellij.ui.content.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.UIBundle;
import com.intellij.ui.content.*;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class ContentManagerImpl implements ContentManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.content.impl.ContentManagerImpl");

  private ContentUI myUI;
  private ArrayList<Content> myContents;
  private EventListenerList myListeners;
  private List<Content> mySelection = new ArrayList<Content>();
  private boolean myCanCloseContents;

  private final Project myProject;

  private final ProjectManagerAdapter myProjectManagerListener;
  private boolean myListenerAdded;

  /**
   * WARNING: as this class adds listener to the ProjectManager which is removed on projectClosed event, all instances of this class
   * must be created on already OPENED projects, otherwise there will be memory leak!
   */
  public ContentManagerImpl(ContentUI contentUI, boolean canCloseContents, Project project) {
    myCanCloseContents = canCloseContents;
    myContents = new ArrayList<Content>();
    myListeners = new EventListenerList();
    myUI = contentUI;
    myUI.setManager(this);
    myProject = project;

    myProjectManagerListener = new ProjectManagerAdapter() {
      public void projectClosed(Project project) {
        if (project == myProject) {
          Content[] contents = myContents.toArray(new Content[myContents.size()]);
          for (Content content : contents) {
            removeContent(content);
          }
        }
      }
    };
  }

  public boolean canCloseContents() {
    return myCanCloseContents;
  }

  public JComponent getComponent() {
    return myUI.getComponent();
  }

  public void addContent(Content content) {
    addContent(content, null);
  }

  public void addContent(final Content content, final Object constraints) {
    try {
      ((ContentImpl)content).setManager(this);
      myContents.add(content);
      fireContentAdded(content, myContents.size() - 1);
      if (myUI.isToSelectAddedContent()) {
        if (myUI.isSingleSelection()) {
          setSelectedContent(content);
        } else {
          addSelectedContent(content);
        }
      }
    } finally {
      addProjectManagerListener();
    }
  }

  // [Valentin] Q: throw exception when failed?
  public boolean removeContent(Content content) {
    try {
      int selectedIndex = myContents.indexOf(mySelection);
      int indexToBeRemoved = myContents.indexOf(content);
      if (indexToBeRemoved < 0) {
        return false;
      }
      if (!fireContentRemoveQuery(content, indexToBeRemoved)) {
        return false;
      }
      if (!content.isValid()) {
        return false; // the content has already been invalidated by another thread or something
      }


      boolean wasSelected = isSelected(content);
      if (wasSelected) {
        removeSelectedContent(content);
      }

      int indexToSelect = -1;
      if (wasSelected) {
        int i = indexToBeRemoved - 1;
        if (i >= 0) {
          indexToSelect = i;
        }
        else if (getContentCount() > 1) {
          indexToSelect = 0;
        }
      }
      else if (selectedIndex > indexToBeRemoved) {
        indexToSelect = selectedIndex - 1;
      }

      myContents.remove(content);

      int newSize = myContents.size();
      if (newSize > 0) {
        if (indexToSelect > -1) {
          final Content toSelect = myContents.get(indexToSelect);
          if (!isSelected(toSelect)) {
            if (myUI.isSingleSelection()) {
              setSelectedContent(toSelect);
            } else {
              addSelectedContent(toSelect);
            }
          }
        }
      }
      else {
        mySelection.clear();
      }
      fireContentRemoved(content, indexToBeRemoved);
      ((ContentImpl)content).setManager(null);

      final Disposable disposer = content.getDisposer();
      if (disposer != null) {
        Disposer.dispose(disposer);
      }

      return true;
    } finally {
      removeProjectManagerListener();
    }
  }

  private void addProjectManagerListener() {
    if (!myListenerAdded && myContents.size() > 0) {
      ProjectManager.getInstance().addProjectManagerListener(myProjectManagerListener);
      myListenerAdded = true;
    }
  }

  private void removeProjectManagerListener() {
    if (myContents.size() == 0) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        myUI.getComponent().updateUI(); //cleanup visibleComponent from Alloy...TabbedPaneUI
      }
      if (myListenerAdded) {
        ProjectManager.getInstance().removeProjectManagerListener(myProjectManagerListener);
        myListenerAdded = false;
      }
    }
  }

  public void removeAllContents() {
    Content[] contents = getContents();
    for (Content content : contents) {
      removeContent(content);
    }
  }

  public int getContentCount() {
    return myContents.size();
  }

  public Content[] getContents() {
    return myContents.toArray(new ContentImpl[myContents.size()]);
  }

  //TODO[anton,vova] is this method needed?
  public Content findContent(String displayName) {
    for (Content content : myContents) {
      if (content.getDisplayName().equals(displayName)) {
        return content;
      }
    }
    return null;
  }

  public Content getContent(int index) {
    if (index >= 0 && index < myContents.size()) {
      return myContents.get(index);
    } else {
      return null;
    }
  }

  public Content getContent(JComponent component) {
    Content[] contents = getContents();
    for (Content content : contents) {
      if (Comparing.equal(component, content.getComponent())) {
        return content;
      }
    }
    return null;
  }

  public int getIndexOfContent(Content content) {
    return myContents.indexOf(content);
  }

  public String getCloseActionName() {
    return UIBundle.message("tabbed.pane.close.tab.action.name");
  }


  public String getCloseAllButThisActionName() {
    return UIBundle.message("tabbed.pane.close.all.tabs.but.this.action.name");
  }

  public List<AnAction> getAdditionalPopupActions(final Content content) {
    return null;
  }

  public boolean canCloseAllContents() {
    if (!canCloseContents()) {
      return false;
    }
    for(Content content: myContents) {
      if (content.isCloseable()) {
        return true;
      }
    }
    return false;
  }

  public void addSelectedContent(final Content content) {
    int index;
    if (content != null) {
      index = getIndexOfContent(content);
      if (index == -1) {
        throw new IllegalArgumentException("content not found: " + content);
      }
    }
    else {
      index = -1;
    }
    if (!isSelected(content)) {
      mySelection.add(content);
      fireSelectionChanged(content);
    }
  }

  public void removeSelectedContent(Content content) {
    if (!isSelected(content)) return;
    mySelection.remove(content);
    fireSelectionChanged(content);
  }

  public boolean isSelected(Content content) {
    return mySelection.contains(content);
  }

  public Content[] getSelectedContents() {
    return myContents.toArray(new Content[myContents.size()]);
  }

  @Nullable
  public Content getSelectedContent() {
    return mySelection.size() > 0 ? mySelection.get(0) : null;
  }

  public void setSelectedContent(Content content) {
    if (isSelected(content)) return;
    final Content[] old = getSelectedContents();
    for (Content each : old) {
      removeSelectedContent(each);
    }

    addSelectedContent(content);
  }

  public void selectPreviousContent() {
    int contentCount = getContentCount();
    LOG.assertTrue(contentCount > 1);
    Content selectedContent = getSelectedContent();
    int index = getIndexOfContent(selectedContent);
    index = (index - 1 + contentCount) % contentCount;
    setSelectedContent(getContent(index));
  }

  public void selectNextContent() {
    int contentCount = getContentCount();
    LOG.assertTrue(contentCount > 1);
    Content selectedContent = getSelectedContent();
    int index = getIndexOfContent(selectedContent);
    index = (index + 1) % contentCount;
    setSelectedContent(getContent(index));
  }

  public void addContentManagerListener(ContentManagerListener l) {
    myListeners.add(ContentManagerListener.class, l);
  }

  public void removeContentManagerListener(ContentManagerListener l) {
    myListeners.remove(ContentManagerListener.class, l);
  }


  protected void fireContentAdded(Content content, int newIndex) {
    ContentManagerEvent event = new ContentManagerEvent(this, content, newIndex);
    ContentManagerListener[] listeners = myListeners.getListeners(ContentManagerListener.class);
    for (ContentManagerListener listener : listeners) {
      listener.contentAdded(event);
    }
  }

  protected void fireContentRemoved(Content content, int oldIndex) {
    ContentManagerEvent event = new ContentManagerEvent(this, content, oldIndex);
    ContentManagerListener[] listeners = myListeners.getListeners(ContentManagerListener.class);
    for (ContentManagerListener listener : listeners) {
      listener.contentRemoved(event);
    }
  }

  protected void fireSelectionChanged(Content content) {
    ContentManagerEvent event = new ContentManagerEvent(this, content, myContents.indexOf(content));
    ContentManagerListener[] listeners = myListeners.getListeners(ContentManagerListener.class);
    for (ContentManagerListener listener : listeners) {
      listener.selectionChanged(event);
    }
  }

  protected boolean fireContentRemoveQuery(Content content, int oldIndex) {
    ContentManagerEvent event = new ContentManagerEvent(this, content, oldIndex);
    ContentManagerListener[] listeners = myListeners.getListeners(ContentManagerListener.class);
    for (ContentManagerListener listener : listeners) {
      listener.contentRemoveQuery(event);
      if (event.isConsumed()) {
        return false;
      }
    }
    return true;
  }

  public void requestFocus(Content content) {
    Content toSelect = content == null ? getSelectedContent() : content;
    if (toSelect == null) return;
    assert myContents.contains(toSelect);

    final JComponent toFocus = toSelect.getPreferredFocusableComponent();
    toFocus.requestFocus();
  }
}
