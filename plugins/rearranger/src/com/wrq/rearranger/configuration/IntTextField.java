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

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.text.NumberFormat;

/**
 * A JTextField tied to a numeric field.  Initialize the JTextField from the field and update
 * the field whenever the JTextField's text changes.
 */
public class IntTextField
  extends JFormattedTextField
{
  public interface IGetSet {
    public int get();

    public void set(int value);
  }

  public IntTextField(final IGetSet getset, final Integer minimum, final Integer maximum) {
    final NumberFormat integerInstance = NumberFormat.getIntegerInstance();
    int maxDigits = 2;
    if (maximum != null && (maximum) > 99) {
      maxDigits = maximum.toString().length();
    }
    integerInstance.setMaximumIntegerDigits(maxDigits);
    integerInstance.setMinimumIntegerDigits(1);
    setFormatterFactory(new DefaultFormatterFactory(new NumberFormatter(integerInstance)));
    setValue(maximum != null ? maximum : new Integer("88"));
    final Dimension d = getPreferredSize();
    d.width += 3;
    setPreferredSize(d);
    setValue(getset.get());
    setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
    setText("" + getValue());
    getDocument().addDocumentListener(new DocumentListener() {
      private void setit() {
        try {
          // allow empty string, else must be valid number
          if (getText().length() != 0) {
            int value = Integer.parseInt(getText());
            if (minimum != null) {
              if (value < (minimum)) value = (minimum);
            }
            if (maximum != null) {
              if (value > (maximum)) value = (maximum);
            }
            getset.set(value);
          }
        }
        catch (NumberFormatException e) {
          // leave value alone
        }
      }

      public void insertUpdate(DocumentEvent e) {
        setit();
      }

      public void removeUpdate(DocumentEvent e) {
        setit();
      }

      public void changedUpdate(DocumentEvent e) {
        setit();
      }
    });
  }
}
