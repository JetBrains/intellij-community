/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.application.options;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

public abstract class CodeStyleAbstractConfigurable implements Configurable {
  private CodeStyleAbstractPanel myPanel;
  private final CodeStyleSettings mySettings;
  private final CodeStyleSettings myCloneSettings;
  private final String myDisplayName;

  public CodeStyleAbstractConfigurable(CodeStyleSettings settings, CodeStyleSettings cloneSettings,
                                       final String displayName) {
    mySettings = settings;
    myCloneSettings = cloneSettings;
    myDisplayName = displayName;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public Icon getIcon() {
    return null;
  }

  public JComponent createComponent() {
    myPanel = createPanel(myCloneSettings);
    return myPanel.getPanel();
  }

  protected abstract CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings);

  public void apply() throws ConfigurationException {
    if (myPanel != null) {
      myPanel.apply(mySettings);
    }
  }

  public void reset() {
    if (myPanel != null) {
      myPanel.reset(mySettings);
    }
  }

  public boolean isModified() {
    return myPanel != null && myPanel.isModified(mySettings);
  }

  public void disposeUIResources() {
    if (myPanel != null) {
      myPanel.dispose();
      myPanel = null;
    }
  }

  public CodeStyleAbstractPanel getPanel() {
    return myPanel;
  }
}
