package com.intellij.slicer;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.usageView.UsageInfo;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.Map;

public class SliceManager implements ProjectComponent {
  private final Project myProject;
  private ContentManager myContentManager;
  private final ToolWindowManager myToolWindowManager;
  private final Map<Content, SlicePanel> myContents = new THashMap<Content, SlicePanel>();
  @NonNls private static final String TOOL_WINDOW_ID = "Slice";

  public static SliceManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, SliceManager.class);
  }

  public SliceManager(Project project, ToolWindowManager toolWindowManager) {
    myProject = project;
    myToolWindowManager = toolWindowManager;
  }

  public void projectOpened() {
    final ToolWindow toolWindow= myToolWindowManager.registerToolWindow(TOOL_WINDOW_ID, true, ToolWindowAnchor.BOTTOM );
    myContentManager = toolWindow.getContentManager();
    new ContentManagerWatcher(toolWindow, myContentManager);
  }

  public void projectClosed() {
    myToolWindowManager.unregisterToolWindow(TOOL_WINDOW_ID);
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

  public void slice(PsiElement element) {
    final SliceToolwindowSettings sliceToolwindowSettings = SliceToolwindowSettings.getInstance(myProject);
    SliceUsage usage;              
    if (element instanceof PsiField) {
      usage = new SliceFieldUsage(new UsageInfo(element), null, (PsiField)element);
    }
    else if (element instanceof PsiParameter) {
      usage = new SliceParameterUsage(new UsageInfo(element), (PsiParameter)element, null);
    }
    else {
      usage = new SliceUsage(new UsageInfo(element), null);
    }
    final Content[] myContent = new Content[1];
    final SlicePanel slicePanel = new SlicePanel(myProject, usage) {
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
    myContent[0] = myContentManager.getFactory().createContent(slicePanel, "Dataflow", true);
    myContentManager.addContent(myContent[0]);
    myContentManager.setSelectedContent(myContent[0]);

    ToolWindowManager.getInstance(myProject).getToolWindow(TOOL_WINDOW_ID).activate(new Runnable(){
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
