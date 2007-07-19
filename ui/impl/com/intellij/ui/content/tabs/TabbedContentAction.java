package com.intellij.ui.content.tabs;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.UIBundle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

public abstract class TabbedContentAction extends AnAction {

  protected final ContentManager myManager;

  protected final ShadowAction myShadow;

  protected TabbedContentAction(@NotNull final ContentManager manager, @NotNull AnAction shortcutTemplate, @NotNull String text) {
    super(text);
    myManager = manager;
    myShadow = new ShadowAction(this, shortcutTemplate, manager.getComponent(), new Presentation(text));
  }

  protected TabbedContentAction(@NotNull final ContentManager manager, @NotNull AnAction template) {
    myManager = manager;
    myShadow = new ShadowAction(this, template, manager.getComponent());
  }

  public abstract static class ForContent extends TabbedContentAction {

    protected final Content myContent;

    public ForContent(@NotNull Content content, @NotNull AnAction shortcutTemplate, final String text) {
      super(content.getManager(), shortcutTemplate, text);
      myContent = content;
      Disposer.register(content, myShadow);
    }

    public ForContent(@NotNull Content content, final AnAction template) {
      super(content.getManager(), template);
      myContent = content;
      Disposer.register(content, myShadow);
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myManager.getIndexOfContent(myContent) >= 0);
    }
  }


  public static class CloseAction extends ForContent {

    public CloseAction(Content content) {
      super(content, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ACTIVE_TAB));
    }

    public void actionPerformed(AnActionEvent e) {
      myManager.removeContent(myContent, true);
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myContent != null && myManager.canCloseContents() && myContent.isCloseable());
      presentation.setVisible(myManager.canCloseContents() && myContent.isCloseable());
      presentation.setText(myManager.getCloseActionName());
    }
  }

  public static class CloseAllButThisAction extends ForContent {

    public CloseAllButThisAction(Content content) {
      super(content, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ALL_EDITORS_BUT_THIS), UIBundle.message("tabbed.pane.close.all.but.this.action.name"));
    }

    public void actionPerformed(AnActionEvent e) {
      Content[] contents = myManager.getContents();
      for (Content content : contents) {
        if (myContent != content && content.isCloseable()) {
          myManager.removeContent(content, true);
        }
      }
      myManager.setSelectedContent(myContent);
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setText(myManager.getCloseAllButThisActionName());
      presentation.setEnabled(myContent != null && myManager.canCloseContents() && myManager.getContentCount() > 1);
      presentation.setVisible(myManager.canCloseContents() && hasCloseableContents());
    }

    private boolean hasCloseableContents() {
      Content[] contents = myManager.getContents();
      for (Content content : contents) {
        if (myContent != content && content.isCloseable()) {
          return true;
        }
      }
      return false;
    }
  }

  public static class MyPinTabAction extends ToggleAction {
    private Content myContent;

    public MyPinTabAction(Content content) {
      myContent = content;
      Presentation presentation = getTemplatePresentation();
      presentation.setText(UIBundle.message("tabbed.pane.pin.tab.action.name"));
      presentation.setDescription(UIBundle.message("tabbed.pane.pin.tab.action.description"));
    }

    public boolean isSelected(AnActionEvent event) {
      return myContent != null && myContent.isPinned();
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      myContent.setPinned(flag);
    }

    public void update(AnActionEvent event) {
      super.update(event);
      Presentation presentation = event.getPresentation();
      boolean enabled = myContent != null && myContent.isPinnable();
      presentation.setEnabled(enabled);
      presentation.setVisible(enabled);
    }
  }

  public static class CloseAllAction extends TabbedContentAction {
    public CloseAllAction(ContentManager manager) {
      super(manager, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ALL_EDITORS), UIBundle.message("tabbed.pane.close.all.action.name"));
    }

    public void actionPerformed(AnActionEvent e) {
      Content[] contents = myManager.getContents();
      for (Content content : contents) {
        if (content.isCloseable()) {
          myManager.removeContent(content, true);
        }
      }
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myManager.canCloseAllContents());
      presentation.setVisible(myManager.canCloseAllContents());
    }
  }
  public static class MyNextTabAction extends TabbedContentAction {
    public MyNextTabAction(ContentManager manager) {
      super(manager, ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_TAB));
    }

    public void actionPerformed(AnActionEvent e) {
      myManager.selectNextContent();
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myManager.getContentCount() > 1);
    }
  }

  public static class MyPreviousTabAction extends TabbedContentAction {
    public MyPreviousTabAction(ContentManager manager) {
      super(manager, ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_TAB));
    }

    public void actionPerformed(AnActionEvent e) {
      myManager.selectPreviousContent();
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myManager.getContentCount() > 1);
    }
  }


}
