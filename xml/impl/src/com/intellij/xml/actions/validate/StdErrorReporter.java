/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.actions.validate;

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.content.*;
import com.intellij.util.ui.ErrorTreeView;
import com.intellij.util.ui.MessageCategory;
import com.intellij.xml.XmlBundle;
import org.xml.sax.SAXParseException;

import javax.swing.*;
import java.util.concurrent.Future;

public class StdErrorReporter extends ErrorReporter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.actions.validate.StdErrorReporter");
  private static final Key<NewErrorTreeViewPanel> KEY = Key.create("ValidateXmlAction.KEY");

  private final NewErrorTreeViewPanel myErrorsView;
  private final String CONTENT_NAME = XmlBundle.message("xml.validate.tab.content.title");
  private final Project myProject;
  private boolean myErrorsDetected = false;

  public StdErrorReporter(ValidateXmlActionHandler handler, Project project, Runnable rerunAction) {
    super(handler);
    myProject = project;
    myErrorsView = new NewErrorTreeViewPanel(project, null, true, true, rerunAction);
  }

  public void startProcessing() {
    final Runnable task = new Runnable() {
      public void run() {
        try {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              StdErrorReporter.super.startProcessing();
            }
          });

          SwingUtilities.invokeLater(
            new Runnable() {
              public void run() {
                if (!myErrorsDetected) {
                  SwingUtilities.invokeLater(
                    new Runnable() {
                      public void run() {
                        removeCompileContents(null);
                        WindowManager.getInstance().getStatusBar(myProject).setInfo(
                          XmlBundle.message("xml.validate.no.errors.detected.status.message"));
                      }
                    }
                  );
                }
              }
            }
          );
        }
        finally {
          boolean b = Thread.interrupted(); // reset interrupted
        }
      }
    };

    final MyProcessController processController = new MyProcessController();
    myErrorsView.setProcessController(processController);
    openMessageView();
    processController.setFuture( ApplicationManager.getApplication().executeOnPooledThread(task) );

    ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
  }

  private void openMessageView() {
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
        myProject, new Runnable() {
          public void run() {
            MessageView messageView = MessageView.SERVICE.getInstance(myProject);
            final Content content = ContentFactory.SERVICE.getInstance().createContent(myErrorsView.getComponent(), CONTENT_NAME, true);
            content.putUserData(KEY, myErrorsView);
            messageView.getContentManager().addContent(content);
            messageView.getContentManager().setSelectedContent(content);
            messageView.getContentManager().addContentManagerListener(new CloseListener(content, messageView.getContentManager()));
            removeCompileContents(content);
            messageView.getContentManager().addContentManagerListener(new MyContentDisposer(content, messageView));
          }
        },
        XmlBundle.message("validate.xml.open.message.view.command.name"),
        null
    );
  }
  private void removeCompileContents(Content notToRemove) {
    MessageView messageView = MessageView.SERVICE.getInstance(myProject);

    for (Content content : messageView.getContentManager().getContents()) {
      if (content.isPinned()) continue;
      if (CONTENT_NAME.equals(content.getDisplayName()) && content != notToRemove) {
        ErrorTreeView listErrorView = (ErrorTreeView)content.getComponent();
        if (listErrorView != null) {
          if (messageView.getContentManager().removeContent(content, true)) {
            content.release();
          }
        }
      }
    }
  }

  public void processError(final SAXParseException ex, final ValidateXmlActionHandler.ProblemType problemType) {
    if (LOG.isDebugEnabled()) {
      String error = myHandler.buildMessageString(ex);
      LOG.debug("enter: processError(error='" + error + "')");
    }

    myErrorsDetected = true;

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      SwingUtilities.invokeLater(
          new Runnable() {
            public void run() {
              final VirtualFile file = myHandler.getFile(ex.getPublicId(), ex.getSystemId());
              myErrorsView.addMessage(
                  problemType == ValidateXmlActionHandler.ProblemType.WARNING ? MessageCategory.WARNING : MessageCategory.ERROR,
                  new String[]{ex.getLocalizedMessage()},
                  file,
                  ex.getLineNumber() - 1,
                  ex.getColumnNumber() - 1,
                  null
              );
            }
          }
      );
    }
  }

  private static class MyContentDisposer implements ContentManagerListener {
    private final Content myContent;
    private final MessageView myMessageView;

    public MyContentDisposer(final Content content, final MessageView messageView) {
      myContent = content;
      myMessageView = messageView;
    }

    public void contentRemoved(ContentManagerEvent event) {
      final Content eventContent = event.getContent();
      if (!eventContent.equals(myContent)) {
        return;
      }
      myMessageView.getContentManager().removeContentManagerListener(this);
      NewErrorTreeViewPanel errorTreeView = eventContent.getUserData(KEY);
      if (errorTreeView != null) {
        errorTreeView.dispose();
      }
      eventContent.putUserData(KEY, null);
    }

    public void contentAdded(ContentManagerEvent event) {
    }
    public void contentRemoveQuery(ContentManagerEvent event) {
    }
    public void selectionChanged(ContentManagerEvent event) {
    }
  }

  private class CloseListener extends ContentManagerAdapter {
    private Content myContent;
    private final ContentManager myContentManager;

    public CloseListener(Content content, ContentManager contentManager) {
      myContent = content;
      myContentManager = contentManager;
    }

    public void contentRemoved(ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        myErrorsView.stopProcess();

        myContentManager.removeContentManagerListener(this);
        myContent.release();
        myContent = null;
      }
    }

    public void contentRemoveQuery(ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        if (!myErrorsView.isProcessStopped()) {
          int result = Messages.showYesNoDialog(
            XmlBundle.message("xml.validate.validation.is.running.terminate.confirmation.text"),
            XmlBundle.message("xml.validate.validation.is.running.terminate.confirmation.title"),
            Messages.getQuestionIcon()
          );
          if (result != 0) {
            event.consume();
          }
        }
      }
    }
  }

  private static class MyProcessController implements NewErrorTreeViewPanel.ProcessController {
    private Future<?> myFuture;

    public void setFuture(Future<?> future) {
      myFuture = future;
    }

    public void stopProcess() {
      if (myFuture != null) {
        myFuture.cancel(true);
      }
    }

    public boolean isProcessStopped() {
      return myFuture != null && myFuture.isDone();
    }
  }
}
