// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.actions.validate;

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.*;
import com.intellij.util.ui.MessageCategory;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXParseException;

import java.util.concurrent.Future;

public final class StdErrorReporter extends ErrorReporter {
  private static final Logger LOG = Logger.getInstance(StdErrorReporter.class);
  private static final Key<NewErrorTreeViewPanel> KEY = Key.create("ValidateXmlAction.KEY");

  private final NewErrorTreeViewPanel myErrorsView;
  private final @TabTitle String myContentName;
  private final Project myProject;

  public StdErrorReporter(ValidateXmlActionHandler handler, PsiFile psiFile, Runnable rerunAction) {
    super(handler);
    myProject = psiFile.getProject();
    myContentName =  XmlBundle.message("xml.validate.tab.content.title", psiFile.getName());
    myErrorsView = new NewErrorTreeViewPanel(myProject, null, true, true, rerunAction);
    myErrorsView.getEmptyText().setText(XmlBundle.message("no.errors.found"));
  }

  @Override
  public void startProcessing() {
    final MyProcessController processController = new MyProcessController();
    myErrorsView.setProcessController(processController);
    openMessageView();
    processController.setFuture(ApplicationManager.getApplication().executeOnPooledThread(
      () -> ApplicationManager.getApplication().runReadAction(() -> super.startProcessing())));

    ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
  }

  private void openMessageView() {
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
      myProject, () -> {
        MessageView messageView = MessageView.SERVICE.getInstance(myProject);
        final Content content = ContentFactory.SERVICE.getInstance().createContent(myErrorsView.getComponent(), myContentName, true);
        content.putUserData(KEY, myErrorsView);
        messageView.getContentManager().addContent(content);
        messageView.getContentManager().setSelectedContent(content);
        messageView.getContentManager().addContentManagerListener(new CloseListener(content, messageView.getContentManager()));
        ContentManagerUtil.cleanupContents(content, myProject, myContentName);
        messageView.getContentManager().addContentManagerListener(new MyContentDisposer(content, messageView));
      },
      XmlBundle.message("xml.validate.open.message.view.command.name"),
      null
    );
  }

  @Override
  public void processError(final SAXParseException ex, final ValidateXmlActionHandler.ProblemType problemType) {
    if (LOG.isDebugEnabled()) {
      String error = myHandler.buildMessageString(ex);
      LOG.debug("enter: processError(error='" + error + "')");
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(() -> {
        final VirtualFile file = myHandler.getProblemFile(ex);
        myErrorsView.addMessage(
            problemType == ValidateXmlActionHandler.ProblemType.WARNING ? MessageCategory.WARNING : MessageCategory.ERROR,
            new String[]{ex.getLocalizedMessage()},
            file,
            ex.getLineNumber() - 1,
            ex.getColumnNumber() - 1,
            null
        );
      }
    );
  }

  private static class MyContentDisposer implements ContentManagerListener {
    private final Content myContent;
    private final MessageView myMessageView;

    MyContentDisposer(final Content content, final MessageView messageView) {
      myContent = content;
      myMessageView = messageView;
    }

    @Override
    public void contentRemoved(@NotNull ContentManagerEvent event) {
      final Content eventContent = event.getContent();
      if (!eventContent.equals(myContent)) {
        return;
      }
      myMessageView.getContentManager().removeContentManagerListener(this);
      NewErrorTreeViewPanel errorTreeView = eventContent.getUserData(KEY);
      if (errorTreeView != null) {
        Disposer.dispose(errorTreeView);
      }
      eventContent.putUserData(KEY, null);
    }
  }

  private class CloseListener implements ContentManagerListener {
    private Content myContent;
    private final ContentManager myContentManager;

    CloseListener(Content content, ContentManager contentManager) {
      myContent = content;
      myContentManager = contentManager;
    }

    @Override
    public void contentRemoved(@NotNull ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        myErrorsView.stopProcess();

        myContentManager.removeContentManagerListener(this);
        myContent.release();
        myContent = null;
      }
    }

    @Override
    public void contentRemoveQuery(@NotNull ContentManagerEvent event) {
      if (event.getContent() == myContent &&
          !myErrorsView.isProcessStopped() &&
          !MessageDialogBuilder.yesNo(XmlBundle.message("xml.validate.validation.is.running.terminate.confirmation.title"),
                                      XmlBundle.message("xml.validate.validation.is.running.terminate.confirmation.text"))
            .ask(myProject)) {
        event.consume();
      }
    }
  }

  private static class MyProcessController implements NewErrorTreeViewPanel.ProcessController {
    private Future<?> myFuture;

    public void setFuture(Future<?> future) {
      myFuture = future;
    }

    @Override
    public void stopProcess() {
      if (myFuture != null) {
        myFuture.cancel(true);
      }
    }

    @Override
    public boolean isProcessStopped() {
      return myFuture != null && myFuture.isDone();
    }
  }
}
