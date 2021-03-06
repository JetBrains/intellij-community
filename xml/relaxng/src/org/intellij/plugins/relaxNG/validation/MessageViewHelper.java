/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.relaxNG.validation;

import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.*;
import com.intellij.util.ui.MessageCategory;
import org.intellij.plugins.relaxNG.RelaxngBundle;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class MessageViewHelper {
  private static final Logger LOG = Logger.getInstance(MessageViewHelper.class);

  private final Project myProject;

  private final Set<String> myErrors = new HashSet<>();

  private final @TabTitle String myContentName;
  private final Key<NewErrorTreeViewPanel> myKey;

  private NewErrorTreeViewPanel myErrorsView;
  private NewErrorTreeViewPanel.ProcessController myProcessController = MyProcessController.INSTANCE;

  public MessageViewHelper(Project project, @TabTitle String contentName, Key<NewErrorTreeViewPanel> key) {
    myProject = project;
    myContentName = contentName;
    myKey = key;
  }

  public synchronized void setProcessController(NewErrorTreeViewPanel.ProcessController processController) {
    if (myErrorsView == null) {
      myProcessController = processController;
    } else {
      myErrorsView.setProcessController(processController);
    }
  }

  public synchronized void openMessageView(Runnable rerun) {
    assert myErrorsView == null;
    myErrorsView = new NewErrorTreeViewPanel(myProject, null, true, true, rerun);
    openMessageViewImpl();
  }

  public synchronized void processError(final SAXParseException ex, final boolean warning) {
    if (myErrors.size() == 0 && myErrorsView == null) {
      myErrorsView = new NewErrorTreeViewPanel(myProject, null, true, true, null);
      myErrorsView.setProcessController(myProcessController);
      openMessageViewImpl();
    }
    final String error = ex.getLineNumber() + "|" + ex.getColumnNumber() + "|" + ex.getSystemId() + "|" + ex.getLocalizedMessage();
    if (!myErrors.add(error)) {
      return;
    }

    VirtualFile file = null;
    final String systemId = ex.getSystemId();
    if (systemId != null) {
      try {
        file = VfsUtil.findFileByURL(new URL(systemId));
      } catch (MalformedURLException e) {
        LOG.warn("systemId = " + systemId);
        LOG.error(e);
      }
    }

    final VirtualFile file1 = file;
    ApplicationManager.getApplication().invokeLater(
      () -> myErrorsView.addMessage(
        warning ? MessageCategory.WARNING : MessageCategory.ERROR,
        new String[]{ex.getLocalizedMessage()},
        file1,
        ex.getLineNumber() - 1,
        ex.getColumnNumber() - 1, null)
    );
  }

  public void close() {
    ContentManagerUtil.cleanupContents(null, myProject, myContentName);
  }

  private void openMessageViewImpl() {
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, () -> {
      MessageView messageView = MessageView.SERVICE.getInstance(myProject);
      Content content = ContentFactory.SERVICE.getInstance().createContent(myErrorsView.getComponent(), myContentName, true);
      content.putUserData(myKey, myErrorsView);
      messageView.getContentManager().addContent(content);
      messageView.getContentManager().setSelectedContent(content);
      messageView.getContentManager().addContentManagerListener(new CloseListener(content, myContentName, myErrorsView));
      ContentManagerUtil.cleanupContents(content, myProject, myContentName);
      messageView.getContentManager().addContentManagerListener(new MyContentDisposer(content, messageView, myKey));
    }, ExecutionBundle.message("open.message.view"), null);

    ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW).activate(null);
  }

  private static class MyProcessController implements NewErrorTreeViewPanel.ProcessController {
    public static final MyProcessController INSTANCE = new MyProcessController();

    @Override
    public void stopProcess() {
    }

    @Override
    public boolean isProcessStopped() {
      return true;
    }
  }

  private static class CloseListener implements ContentManagerListener {
    private final String myContentName;

    private NewErrorTreeViewPanel myErrorsView;
    private Content myContent;

    CloseListener(Content content, String contentName, NewErrorTreeViewPanel errorsView) {
      myContent = content;
      myContentName = contentName;
      myErrorsView = errorsView;
    }

    @Override
    public void contentRemoved(@NotNull ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        if (myErrorsView.canControlProcess()) {
          myErrorsView.stopProcess();
        }
        myErrorsView = null;

        Objects.requireNonNull(myContent.getManager()).removeContentManagerListener(this);
        myContent.release();
        myContent = null;
      }
    }

    @Override
    public void contentRemoveQuery(@NotNull ContentManagerEvent event) {
      if (event.getContent() == myContent && myErrorsView != null && myErrorsView.canControlProcess() && !myErrorsView.isProcessStopped()) {
        if (!MessageDialogBuilder.yesNo(RelaxngBundle.message("relaxng.message-viewer.warning.message", myContentName),
                                       RelaxngBundle.message("relaxng.message-viewer.warning.title", myContentName))
              .ask(myErrorsView)) {
          event.consume();
        }
      }
    }
  }

  private static class MyContentDisposer implements ContentManagerListener {
    private final Content myContent;
    private final MessageView myMessageView;
    private final Key<NewErrorTreeViewPanel> myKey;

    MyContentDisposer(final Content content, final MessageView messageView, Key<NewErrorTreeViewPanel> key) {
      myContent = content;
      myMessageView = messageView;
      myKey = key;
    }

    @Override
    public void contentRemoved(@NotNull ContentManagerEvent event) {
      final Content eventContent = event.getContent();
      if (!eventContent.equals(myContent)) {
        return;
      }
      myMessageView.getContentManager().removeContentManagerListener(this);
      NewErrorTreeViewPanel errorTreeView = eventContent.getUserData(myKey);
      if (errorTreeView != null) {
        Disposer.dispose(errorTreeView);
      }
      eventContent.putUserData(myKey, null);
    }
  }

  public class ErrorHandler extends DefaultHandler {
    private boolean myHadErrorOrWarning;

    @Override
    public void warning(SAXParseException e) {
      myHadErrorOrWarning = true;
      processError(e, true);
    }

    @Override
    public void error(SAXParseException e) {
      myHadErrorOrWarning = true;
      processError(e, false);
    }

    @Override
    public void fatalError(SAXParseException e) {
      myHadErrorOrWarning = true;
      processError(e, false);
    }

    public boolean hadErrorOrWarning() {
      return myHadErrorOrWarning;
    }
  }
}
