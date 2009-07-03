package com.intellij.slicer;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

public class SliceManager {
  private final Project myProject;
  private final ContentManager myContentManager;
  private static final String TOOL_WINDOW_ID = "Dataflow to this";
  private volatile boolean myCanceled;

  public static SliceManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, SliceManager.class);
  }

  public SliceManager(Project project, ToolWindowManager toolWindowManager, final Application application) {
    myProject = project;
    final ToolWindow toolWindow= toolWindowManager.registerToolWindow(TOOL_WINDOW_ID, true, ToolWindowAnchor.BOTTOM, project);
    myContentManager = toolWindow.getContentManager();
    new ContentManagerWatcher(toolWindow, myContentManager);
    final MyApplicationListener myApplicationListener = new MyApplicationListener();
    application.addApplicationListener(myApplicationListener);
    Disposer.register(project, new Disposable() {
      public void dispose() {
        application.removeApplicationListener(myApplicationListener);
      }
    });
  }

  public void slice(PsiElement element) {
    final SliceToolwindowSettings sliceToolwindowSettings = SliceToolwindowSettings.getInstance(myProject);
    SliceUsage usage = createSliceUsage(element);
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
  }

  public static SliceUsage createSliceUsage(PsiElement element) {
    UsageInfo usageInfo = new UsageInfo(element);
    SliceUsage usage;
    if (element instanceof PsiField) {
      usage = new SliceFieldUsage(usageInfo, null, (PsiField)element);
    }
    else if (element instanceof PsiParameter) {
      usage = new SliceParameterUsage(usageInfo, (PsiParameter)element, null);
    }
    else {
      usage = new SliceUsage(usageInfo, null);
    }
    return usage;
  }

  private class MyApplicationListener extends ApplicationAdapter {
    public void beforeWriteActionStart(Object action) {
      myCanceled = true;
    }
  }

  public void checkCanceled() throws ProcessCanceledException {
    if (myCanceled) {
      throw new ProcessCanceledException();
    }
  }

  public void runInterruptibly(Runnable runnable, Runnable onCancel) throws ProcessCanceledException {
    myCanceled = false;
    ProgressIndicatorBase progress = new ProgressIndicatorBase();
    try {
      ProgressManager.getInstance().runProcess(runnable, progress);
    }
    catch (ProcessCanceledException e) {
      myCanceled = true;
      progress.cancel();
      //reschedule for later
      onCancel.run();
      throw e;
    }
  }
}
