package com.intellij.slicer;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
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
import com.intellij.usageView.UsageViewUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class SliceManager implements ProjectComponent {
  private final Project myProject;
  private ContentManager myContentManager;
  private final ToolWindowManager myToolWindowManager;
  private final Map<Content, SlicePanel> myContents = new THashMap<Content, SlicePanel>();
  private static final String TOOL_WINDOW_ID = "Dataflow to this";

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
    for (SlicePanel panel : myContents.values()) {
      panel.dispose();
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
    UsageInfo usageInfo = new UsageInfo(element);
    if (element instanceof PsiField) {
      usage = new SliceFieldUsage(usageInfo, null, (PsiField)element);
    }
    else if (element instanceof PsiParameter) {
      usage = new SliceParameterUsage(usageInfo, (PsiParameter)element, null);
    }
    else {
      usage = new SliceUsage(usageInfo, null);
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
    String title = UsageViewUtil.getDescriptiveName(element);
    if (StringUtil.isEmpty(title)) title = element.getText();
    title = StringUtil.first(title, 20, true);
    myContent[0] = myContentManager.getFactory().createContent(slicePanel, title, true);
    myContentManager.addContent(myContent[0]);
    myContentManager.setSelectedContent(myContent[0]);

    ToolWindowManager.getInstance(myProject).getToolWindow(TOOL_WINDOW_ID).activate(null);
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
