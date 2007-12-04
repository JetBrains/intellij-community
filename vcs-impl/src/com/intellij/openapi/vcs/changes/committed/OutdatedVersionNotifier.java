package com.intellij.openapi.vcs.changes.committed;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.LightColors;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.text.DateFormat;
import java.util.List;

/**
 * @author yole
 */
public class OutdatedVersionNotifier implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.OutdatedVersionNotifier");

  private FileEditorManager myFileEditorManager;
  private CommittedChangesCache myCache;
  private FileEditorManagerListener myFileEditorManagerListener = new MyFileEditorManagerListener();
  private static final Key<OutdatedRevisionPanel> PANEL_KEY = new Key<OutdatedRevisionPanel>("OutdatedRevisionPanel");
  private volatile boolean myIncomingChangesRequested;

  public OutdatedVersionNotifier(FileEditorManager fileEditorManager,
                                 CommittedChangesCache cache,
                                 MessageBus messageBus) {
    myFileEditorManager = fileEditorManager;
    myCache = cache;
    messageBus.connect().subscribe(CommittedChangesCache.COMMITTED_TOPIC, new CommittedChangesAdapter() {
      public void incomingChangesUpdated(@Nullable final List<CommittedChangeList> receivedChanges) {
        if (myCache.getCachedIncomingChanges() == null) {
          requestLoadIncomingChanges();
        }
        else {
          updateAllEditorsLater();
        }
      }
    });
  }

  private void requestLoadIncomingChanges() {
    LOG.info("Requesting load of incoming changes");
    if (!myIncomingChangesRequested) {
      myIncomingChangesRequested = true;
      myCache.loadIncomingChangesAsync(new Consumer<List<CommittedChangeList>>() {
        public void consume(final List<CommittedChangeList> committedChangeLists) {
          myIncomingChangesRequested = false;
          updateAllEditorsLater();
        }
      });
    }
  }

  public void projectOpened() {
    myFileEditorManager.addFileEditorManagerListener(myFileEditorManagerListener);
  }

  public void projectClosed() {
    myFileEditorManager.removeFileEditorManagerListener(myFileEditorManagerListener);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "OutdatedVersionNotifier";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private void updateAllEditorsLater() {
    LOG.info("Queueing update of editors");
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        updateAllEditors();
      }
    });
  }

  private void updateAllEditors() {
    if (myCache.getCachedIncomingChanges() == null) {
      requestLoadIncomingChanges();
      return;
    }
    LOG.info("Updating editors");
    final VirtualFile[] files = myFileEditorManager.getOpenFiles();
    for(VirtualFile file: files) {
      final Pair<CommittedChangeList,Change> pair = myCache.getIncomingChangeList(file);
      final FileEditor[] fileEditors = myFileEditorManager.getEditors(file);
      for(FileEditor editor: fileEditors) {
        final OutdatedRevisionPanel oldPanel = editor.getUserData(PANEL_KEY);
        if (pair != null) {
          if (oldPanel != null) {
            oldPanel.setChangeList(pair.first, pair.second);
          }
          else {
            initPanel(pair.first, pair.second, editor);
          }
        }
        else if (oldPanel != null) {
          myFileEditorManager.removeTopComponent(editor, oldPanel);
          editor.putUserData(PANEL_KEY, null);
        }
      }
    }
  }

  private void initPanel(final CommittedChangeList list, final Change c, final FileEditor editor) {
    final OutdatedRevisionPanel component = new OutdatedRevisionPanel(list, c);
    editor.putUserData(PANEL_KEY, component);
    myFileEditorManager.addTopComponent(editor, component);
  }

  private class MyFileEditorManagerListener implements FileEditorManagerListener {
    public void fileOpened(FileEditorManager source, VirtualFile file) {
      if (myCache.getCachedIncomingChanges() == null) {
        requestLoadIncomingChanges();
      }
      else {
        final Pair<CommittedChangeList, Change> pair = myCache.getIncomingChangeList(file);
        if (pair != null) {
          final FileEditor[] fileEditors = source.getEditors(file);
          for(FileEditor editor: fileEditors) {
            initPanel(pair.first, pair.second, editor);
          }
        }
      }
    }

    public void fileClosed(FileEditorManager source, VirtualFile file) {
    }

    public void selectionChanged(FileEditorManagerEvent event) {
    }
  }

  private static class OutdatedRevisionPanel extends JPanel {
    private CommittedChangeList myChangeList;
    private JLabel myLabel = new JLabel();

    public OutdatedRevisionPanel(CommittedChangeList changeList, final Change c) {
      super(new BorderLayout());
      setBackground(LightColors.YELLOW);
      setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
      myChangeList = changeList;
      updateLabelText(c);
      add(myLabel, BorderLayout.CENTER);

      JPanel linksPanel = new JPanel(new FlowLayout());
      linksPanel.setBackground(LightColors.YELLOW);
      linksPanel.add(createActionLabel(VcsBundle.message("outdated.version.show.diff.action"), "Compare.LastVersion"));
      linksPanel.add(createActionLabel(VcsBundle.message("outdated.version.update.project.action"), "Vcs.UpdateProject"));
      add(linksPanel, BorderLayout.EAST);
    }

    private HyperlinkLabel createActionLabel(final String text, @NonNls final String actionId) {
      HyperlinkLabel label = new HyperlinkLabel(text, Color.BLUE, LightColors.YELLOW, Color.BLUE);
      label.addHyperlinkListener(new HyperlinkListener() {
        public void hyperlinkUpdate(final HyperlinkEvent e) {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            executeAction(actionId);
          }
        }
      });
      return label;
    }

    private void executeAction(final String actionId) {
      final AnAction action = ActionManager.getInstance().getAction(actionId);
      final AnActionEvent event = new AnActionEvent(null, DataManager.getInstance().getDataContext(this), ActionPlaces.UNKNOWN,
                                                    action.getTemplatePresentation(), ActionManager.getInstance(),
                                                    0);
      action.beforeActionPerformedUpdate(event);
      action.update(event);

      if (event.getPresentation().isEnabled() && event.getPresentation().isVisible()) {
        action.actionPerformed(event);
      }
    }

    private void updateLabelText(final Change c) {
      final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
      String comment = myChangeList.getComment();
      int pos = comment.indexOf("\n");
      if (pos >= 0) {
        comment = comment.substring(0, pos).trim() + "...";
      }
      final String key = c.getType() == Change.Type.DELETED ? "outdated.version.text.deleted" : "outdated.version.text";
      myLabel.setText(VcsBundle.message(key, myChangeList.getCommitterName(),
                                        dateFormat.format(myChangeList.getCommitDate()), comment));
    }

    public void setChangeList(final CommittedChangeList changeList, final Change c) {
      myChangeList = changeList;
      updateLabelText(c);
    }
  }
}