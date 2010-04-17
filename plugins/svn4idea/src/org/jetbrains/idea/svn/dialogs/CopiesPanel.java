/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DottedBorder;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;

public class CopiesPanel {
  private final Project myProject;
  private MessageBusConnection myConnection;
  private SvnVcs myVcs;
  private JPanel myPanel;
  private JComponent myHolder;
  private LinkLabel myRefreshLabel;

  public CopiesPanel(final Project project) {
    myProject = project;
    myConnection = myProject.getMessageBus().connect(myProject);
    myVcs = SvnVcs.getInstance(myProject);

    final Runnable focus = new Runnable() {
      public void run() {
        IdeFocusManager.getInstance(myProject).requestFocus(myRefreshLabel, true);
      }
    };
    myConnection.subscribe(SvnVcs.ROOTS_RELOADED, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final List<WCInfo> infoList = myVcs.getAllWcInfos();
            updateList(infoList);
            myRefreshLabel.setEnabled(true);
            SwingUtilities.invokeLater(focus);
          }
        }, ModalityState.NON_MODAL);
      }
    });

    final JPanel holderPanel = new JPanel(new BorderLayout());
    myPanel = new JPanel(new GridBagLayout());
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(myPanel, BorderLayout.NORTH);
    holderPanel.add(panel, BorderLayout.WEST);
    myRefreshLabel = new LinkLabel("Refresh", null, new LinkListener() {
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        if (myRefreshLabel.isEnabled()) {
          myVcs.invokeRefreshSvnRoots(false);
          myRefreshLabel.setEnabled(false);
        }
      }
    });
    myHolder = new JScrollPane(holderPanel);
    setFocusableForLinks(myRefreshLabel);
    initView();
  }

  public JComponent getPrefferedFocusComponent() {
    return myRefreshLabel;
  }

  private JTextField createField(final String text) {
    final JTextField field = new JTextField(text);
    field.setBackground(UIUtil.getPanelBackgound());
    field.setEditable(false);                               
    field.setBorder(null);
    field.setFocusable(false);
    field.setHorizontalAlignment(JTextField.RIGHT);
    field.setCaretPosition(0);
    return field;
  }

  private void updateList(final List<WCInfo> infoList) {
    myPanel.removeAll();
    final Insets nullIndent = new Insets(1, 3, 1, 0);
    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(2, 2, 0, 0), 0, 0);
    gb.insets.left = 4;
    myPanel.add(myRefreshLabel, gb);
    gb.insets.left = 1;

    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final Insets topIndent = new Insets(10, 3, 0, 0);
    for (final WCInfo wcInfo : infoList) {
      final JPanel copyPanel = new JPanel(new GridBagLayout());

      final GridBagConstraints gb1 =
        new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, nullIndent, 0, 0);
      gb1.insets.top = 1;
      gb1.gridwidth = 3;

      gb.insets = topIndent;
      gb.fill = GridBagConstraints.HORIZONTAL;
      ++ gb.gridy;

      final JPanel contForCopy = new JPanel(new BorderLayout());
      contForCopy.add(copyPanel, BorderLayout.WEST);
      myPanel.add(contForCopy, gb);

      final JTextField path = createField(wcInfo.getPath());
      copyPanel.add(path, gb1);
      path.setFont(path.getFont().deriveFont(Font.BOLD));

      gb1.insets = nullIndent;
      gb1.insets.top = 5;
      ++ gb1.gridy;
      final JTextField url = createField("URL: " + wcInfo.getRootUrl());
      copyPanel.add(url, gb1);

      ++ gb1.gridy;
      gb1.insets.top = 1;
      final JTextField format = createField("Format: " + wcInfo.getFormat().getName());
      copyPanel.add(format, gb1);

      final NestedCopyType type = wcInfo.getType();
      if (NestedCopyType.external.equals(type) || NestedCopyType.switched.equals(type)) {
        ++ gb1.gridy;
        final JTextField whetherNested = createField(type.getName() + " copy");
        copyPanel.add(whetherNested, gb1);
        whetherNested.setFont(whetherNested.getFont().deriveFont(Font.ITALIC));
      }
      if (wcInfo.isIsWcRoot()) {
        ++ gb1.gridy;
        final JTextField whetherRoot = createField("Working copy root");
        copyPanel.add(whetherRoot, gb1);
        whetherRoot.setFont(whetherRoot.getFont().deriveFont(Font.ITALIC));
      }

      gb1.gridwidth = 1;
      gb1.insets.top = 5;
      ++ gb1.gridy;
      final LinkLabel formatLabel = new LinkLabel("Change Format", null, new LinkListener() {
        public void linkSelected(LinkLabel aSource, Object aLinkData) {
          changeFormat(wcInfo);
        }
      });
      copyPanel.add(formatLabel, gb1);
      setFocusableForLinks(formatLabel);

      final VirtualFile vf = lfs.refreshAndFindFileByIoFile(new File(wcInfo.getPath()));
      final VirtualFile root = (vf == null) ? wcInfo.getVcsRoot() : vf;
      ++ gb1.gridx;
      final LinkLabel configureBranchesLabel = new LinkLabel("Configure Branches", null, new LinkListener() {
        public void linkSelected(LinkLabel aSource, Object aLinkData) {
          BranchConfigurationDialog.configureBranches(myProject, root, true);
        }
      });
      if (root == null) {
        configureBranchesLabel.setEnabled(false); //+-
      }
      copyPanel.add(configureBranchesLabel, gb1);
      setFocusableForLinks(configureBranchesLabel);
    }

    myPanel.revalidate();
    myPanel.repaint();
  }

  private void setFocusableForLinks(final LinkLabel label) {
    final Border border = new DottedBorder(new Insets(1,2,1,1), Color.black);
    label.setFocusable(true);
    label.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        super.focusGained(e);
        label.setBorder(border);
      }

      @Override
      public void focusLost(FocusEvent e) {
        super.focusLost(e);
        label.setBorder(null);
      }
    });
    label.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          label.doClick();
        }
      }
    });
  }

  private void changeFormat(final WCInfo wcInfo) {
    ChangeFormatDialog dialog = new ChangeFormatDialog(myProject, new File(wcInfo.getPath()), false, ! wcInfo.isIsWcRoot());
    dialog.setData(true, wcInfo.getFormat().getOption());
    dialog.show();
    if (! dialog.isOK()) {
      return;
    }
    final String newMode = dialog.getUpgradeMode();
    if (! wcInfo.getFormat().getOption().equals(newMode)) {
      final WorkingCopyFormat newFormat = WorkingCopyFormat.getInstance(newMode);
      final Task.Backgroundable task = new SvnFormatWorker(myProject, newFormat, wcInfo) {
        @Override
        public void onSuccess() {
          super.onSuccess();
          myRefreshLabel.doClick();
        }
      };
      ProgressManager.getInstance().run(task);
    }
  }

  private void initView() {
    myRefreshLabel.doClick();
  }

  public JComponent getComponent() {
    return myHolder;
  }
}
