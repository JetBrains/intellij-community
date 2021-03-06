// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.properties.PropertyValue;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static org.jetbrains.idea.svn.SvnBundle.message;

public class SetKeywordsDialog extends DialogWrapper {
  private static final List<@NlsSafe String> KNOWN_KEYWORDS =
    newArrayList("Id", "HeadURL", "LastChangedDate", "LastChangedRevision", "LastChangedBy");

  private static final Map<String, String> KNOWN_KEYWORD_ALIASES = ContainerUtil.<@NlsSafe String, @NlsSafe String>immutableMapBuilder()
    .put("URL", "HeadURL")
    .put("Date", "LastChangedDate")
    .put("Revision", "LastChangedRevision")
    .put("Rev", "LastChangedRevision")
    .put("Author", "LastChangedBy")
    .build();

  @Nullable private final PropertyValue myKeywordsValue;
  @NotNull private final List<JCheckBox> myKeywordOptions;

  protected SetKeywordsDialog(Project project, @Nullable PropertyValue keywordsValue) {
    super(project, false);
    myKeywordOptions = new ArrayList<>();
    myKeywordsValue = keywordsValue;

    setTitle(message("dialog.title.svn.keywords"));
    setResizable(false);
    init();
  }

  @Nullable
  public String getKeywords() {
    List<JCheckBox> selectedKeywords = ContainerUtil.filter(myKeywordOptions, keywordOption -> keywordOption.isSelected());

    return StringUtil.nullize(StringUtil.join(selectedKeywords, keywordOption -> keywordOption.getText(), " "));
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JBLabel(message("label.select.keywords.to.set")), BorderLayout.NORTH);
    JPanel buttonsPanel = new JPanel(new GridLayout(5, 1));

    for (@NlsSafe String keyword : KNOWN_KEYWORDS) {
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
    Set<String> keywords = parseKeywords(myKeywordsValue);

    for (JCheckBox keywordOption : myKeywordOptions) {
      keywordOption.setSelected(keywords.contains(keywordOption.getText()));
    }
  }

  /**
   * TODO: Subversion 1.8 also allow defining custom keywords (in "svn:keywords" property value). But currently it is unnecessary for this
   * TODO: dialog.
   */
  @NotNull
  private static Set<String> parseKeywords(@Nullable PropertyValue keywordsValue) {
    Set<String> result = new HashSet<>();

    if (keywordsValue != null) {
      for (String keyword : StringUtil.split(PropertyValue.toString(keywordsValue), " ")) {
        result.add(ObjectUtils.notNull(KNOWN_KEYWORD_ALIASES.get(keyword), keyword));
      }
    }

    return result;
  }
}
