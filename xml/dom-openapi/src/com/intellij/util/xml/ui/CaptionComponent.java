/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.util.xml.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;

public class CaptionComponent extends JPanel implements Committable, Highlightable {
  private JPanel myRootPanel;
  private JLabel myCaptionLabel;
  private JLabel myDescriptionLabel;
  private JLabel myIconLabel;
  private JPanel myErrorPanel;
  private CommittablePanel myCommittableErrorPanel;

  private boolean myBordered = true;

  public CaptionComponent() {
    this(null);
  }

  public CaptionComponent(@Nls String text) {
    this(text, null);
  }

  public CaptionComponent(@Nls String text, Icon icon) {
    super(new BorderLayout());
    updateBorder();
    myRootPanel.setBackground(new JBColor(new Color(243, 244, 229), new Color(42, 55, 62)));
    add(myRootPanel, BorderLayout.CENTER);

    setText(text);
    setIcon(icon);
  }

  private void updateBorder() {
    if (myBordered) {
      myRootPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.LIGHT_GRAY));
    }
    else {
      myRootPanel.setBorder(BorderFactory.createEmptyBorder());
    }
  }

  @Override
  public void updateHighlighting() {
    CommittableUtil.updateHighlighting(myCommittableErrorPanel);
  }

  public void setText(final @Nls String text) {
    if (text == null) return;

    myCaptionLabel.setText(text);
  }

  @Nls
  public String getText() {
    return myCaptionLabel.getText();
  }

  public void setIcon(final Icon icon) {
    myIconLabel.setVisible(icon != null);
    myIconLabel.setIcon(icon);
  }

  public Icon getIcon() {
    return myIconLabel.getIcon();
  }

  public boolean isBordered() {
    return myBordered;
  }

  public String getDescriptionText() {
    return myDescriptionLabel.getText();
  }

  public void setDescriptionText(final @Nls String text) {
    myDescriptionLabel.setVisible(text != null && text.trim().length() > 0);

    myDescriptionLabel.setText(text);
  }

  public final void setBordered(final boolean bordered) {
    myBordered = bordered;
    updateBorder();
  }

  @Override
  public final void commit() {
  }

  @Override
  public void reset() {
    if (myCommittableErrorPanel != null) myCommittableErrorPanel.reset();
  }

  @Override
  public final void dispose() {
  }

  public void initErrorPanel(final CommittablePanel errorPanel) {
    myCommittableErrorPanel = errorPanel;
    Disposer.register(this, errorPanel);
    
    final JComponent component = errorPanel.getComponent();

    component.setBackground(getBackground());
    
    myErrorPanel.setLayout(new BorderLayout());
    myErrorPanel.add(component, BorderLayout.CENTER);
  }
}
