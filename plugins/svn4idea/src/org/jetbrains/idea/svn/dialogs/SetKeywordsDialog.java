/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: Jun 21, 2006
 * Time: 6:37:41 PM
 */
public class SetKeywordsDialog extends DialogWrapper {

  private static final List<String> KNOWN_KEYWORDS =
    ContainerUtil.newArrayList("Id", "HeadURL", "LastChangedDate", "LastChangedRevision", "LastChangedBy");

  @Nullable private final PropertyValue myKeywordsValue;
  @NotNull private final List<JCheckBox> myKeywordOptions;

  protected SetKeywordsDialog(Project project, @Nullable PropertyValue keywordsValue) {
    super(project, false);
    myKeywordOptions = ContainerUtil.newArrayList();
    myKeywordsValue = keywordsValue;

    setTitle("SVN Keywords");
    setResizable(false);
    init();
  }

  @Nullable
  public String getKeywords() {
    List<JCheckBox> selectedKeywords = ContainerUtil.filter(myKeywordOptions, new Condition<JCheckBox>() {
      @Override
      public boolean value(@NotNull JCheckBox keywordOption) {
        return keywordOption.isSelected();
      }
    });

    return StringUtil.nullize(StringUtil.join(selectedKeywords, new Function<JCheckBox, String>() {
      @Override
      public String fun(@NotNull JCheckBox keywordOption) {
        return keywordOption.getText();
      }
    }, " "));
  }

  @Nullable
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel("Select keywords to set: "), BorderLayout.NORTH);
    JPanel buttonsPanel = new JPanel(new GridLayout(5, 1));

    for (String keyword : KNOWN_KEYWORDS) {
      JCheckBox keywordOption = new JCheckBox(keyword);

      myKeywordOptions.add(keywordOption);
      buttonsPanel.add(keywordOption);
    }

    panel.add(buttonsPanel, BorderLayout.CENTER);

    return panel;
  }

  @Override
  protected void init() {
    super.init();

    updateKeywordOptions();
  }

  private void updateKeywordOptions() {
    Map<String, byte[]> keywords = SVNTranslator.computeKeywords(PropertyValue.toString(myKeywordsValue), "u", "a", "d", "r", null);

    for (JCheckBox keywordOption : myKeywordOptions) {
      keywordOption.setSelected(keywords.containsKey(keywordOption.getText()));
    }
  }
}
