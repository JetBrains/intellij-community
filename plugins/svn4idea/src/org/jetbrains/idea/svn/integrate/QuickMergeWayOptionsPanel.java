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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 3/29/13
 * Time: 7:27 PM
 */
public class QuickMergeWayOptionsPanel {
  private JButton myMergeAllButton;
  private JButton myQuickManualSelectButton;
  private JButton mySelectWithPreFilterButton;
  private JButton myCancelButton;
  private JPanel myMainPanel;
  private JLabel myAllNotMergedRevisionsLabel;
  private JLabel myShowsAllRevisionsFromLabel;
  private JLabel myFindsWhereOneOfLabel;
  private DialogWrapper myWrapper;

  private QuickMergeContentsVariants myVariant = QuickMergeContentsVariants.cancel;

  public QuickMergeWayOptionsPanel() {
    myMergeAllButton.addActionListener(setCodeAndClose(QuickMergeContentsVariants.all));
    myQuickManualSelectButton.addActionListener(setCodeAndClose(QuickMergeContentsVariants.showLatest));
    mySelectWithPreFilterButton.addActionListener(setCodeAndClose(QuickMergeContentsVariants.select));
    myCancelButton.addActionListener(setCodeAndClose(QuickMergeContentsVariants.cancel));

    myAllNotMergedRevisionsLabel.setUI(new MultiLineLabelUI());
    myShowsAllRevisionsFromLabel.setUI(new MultiLineLabelUI());
    myFindsWhereOneOfLabel.setUI(new MultiLineLabelUI());

    myAllNotMergedRevisionsLabel.setBorder(BorderFactory.createEmptyBorder(0,0,10,0));
    myShowsAllRevisionsFromLabel.setBorder(BorderFactory.createEmptyBorder(0,0,10,0));
    myFindsWhereOneOfLabel.setBorder(BorderFactory.createEmptyBorder(0,0,10,0));
  }

  private ActionListener setCodeAndClose(final QuickMergeContentsVariants variant) {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myVariant = variant;
        close();
      }
    };
  }

  private void close() {
    myWrapper.close(DialogWrapper.OK_EXIT_CODE);
  }

  public void setWrapper(DialogWrapper wrapper) {
    myWrapper = wrapper;
  }

  public QuickMergeContentsVariants getVariant() {
    return myVariant;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }
}
