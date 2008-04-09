package com.intellij.slicer;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiExpression;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.usageView.UsageInfo;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class SliceManager implements ProjectComponent {
  private final Project myProject;
  private ContentManager myContentManager;
  private final ToolWindowManager myToolWindowManager;
  private final Map<Content, SlicePanel> myContents = new THashMap<Content, SlicePanel>();

  public static SliceManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, SliceManager.class);
  }

  public SliceManager(Project project, ToolWindowManager toolWindowManager) {
    myProject = project;
    myToolWindowManager = toolWindowManager;
  }

  public void projectOpened() {
    final ToolWindow toolWindow= myToolWindowManager.registerToolWindow("Slice", true, ToolWindowAnchor.BOTTOM );
    myContentManager = toolWindow.getContentManager();
    new ContentManagerWatcher(toolWindow, myContentManager);
  }

  public void projectClosed() {
    myToolWindowManager.unregisterToolWindow("Slice");
    for (Content content : myContents.keySet()) {
      SlicePanel slicePanel = myContents.get(content);
      slicePanel.dispose();
    }
  }

  @NotNull
  public String getComponentName() {
    return "SliceManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public void slice(final PsiExpression expression) {
    final Content[] myContent = new Content[1];
    final SliceToolwindowSettings sliceToolwindowSettings = SliceToolwindowSettings.getInstance(myProject);
    final SlicePanel slicePanel = new SlicePanel(myProject, new SliceUsage(new UsageInfo(expression), null)) {
      protected void close() {
        myContentManager.removeContent(myContent[0], true);
      }

      public boolean isAutoScroll() {
        return sliceToolwindowSettings.isAutoScroll();
      }

      public void setAutoScroll(boolean autoScroll) {
        sliceToolwindowSettings.setAutoScroll(autoScroll);
      }

      public boolean isPreview() {
        return sliceToolwindowSettings.isPreview();
      }

      public void setPreview(boolean preview) {
        sliceToolwindowSettings.setPreview(preview);
      }
    };
    myContent[0] = myContentManager.getFactory().createContent(slicePanel, "slices", true);
    myContentManager.addContent(myContent[0]);
    myContentManager.setSelectedContent(myContent[0]);

    ToolWindowManager.getInstance(myProject).getToolWindow("Slice").activate(new Runnable(){
      public void run() {
        //mySlicePanel.sliceFinished();
      }
    });
    myContentManager.addContentManagerListener(new ContentManagerAdapter(){
      public void contentRemoved(final ContentManagerEvent event) {
        Content content = event.getContent();
        if (content == myContent[0]) {
          slicePanel.dispose();
          myContents.remove(content);
          myContentManager.removeContentManagerListener(this);
        }
      }
    });

    myContents.put(myContent[0], slicePanel);
  }
}
