/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.configuration;

import com.wrq.rearranger.settings.CommentRule;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.attributeGroups.ClassAttributes;

import javax.swing.*;

/** UI code for Outer Class order dialog. */
final class ClassOrderSettingsPane
  extends SettingsPane
{
// --------------------------- CONSTRUCTORS ---------------------------

  public ClassOrderSettingsPane(final RearrangerSettings settings) {
    super(settings, settings.getClassOrderAttributeList());
  }

// -------------------------- OTHER METHODS --------------------------

  ChoicePanel createChoicePanel() {
    final ChoiceObject[] choices = new ChoiceObject[2];
    choices[0] = new ChoiceObject("Class") {
      void createObject() {
        choiceObject = new ClassAttributes();
      }

      JPanel createJPanel() {
        return ((ClassAttributes)editingObject).getClassAttributesPanel();
      }
    };
    choices[1] = new ChoiceObject("Separator Comment") {
      void createObject() {
        choiceObject = new CommentRule();
      }

      JPanel createJPanel() {
        return ((CommentRule)editingObject).getCommentPanel();
      }
    };
    final ChoicePanel panel = new ChoicePanel(choices, "Outer Class Order");
    return panel;
  }
}
