/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.wizard;

import javax.swing.*;
import java.awt.event.ActionEvent;

public abstract class WizardAction extends AbstractAction {

  protected WizardModel myModel;

  public WizardAction(String name, WizardModel model) {
    super(name);
    myModel = model;
  }

  protected void setMnemonic(char value) {
    putValue(Action.MNEMONIC_KEY, new Integer(value));
  }

  public final void setName(String name) {
    putValue(Action.NAME, name);
  }

  public static class Next extends WizardAction {

    public Next(WizardModel model) {
      super("Next >", model);
      setMnemonic('N');
    }

    public void actionPerformed(ActionEvent e) {
      myModel.next();
    }
  }

  public static class Previous extends WizardAction {

    public Previous(WizardModel model) {
      super("< Previous", model);
      setMnemonic('P');
    }

    public void actionPerformed(ActionEvent e) {
      myModel.previous();
    }
  }

  public static class Finish extends WizardAction {

    public Finish(WizardModel model) {
      super("Finish", model);
      setMnemonic('F');
    }

    public void actionPerformed(ActionEvent e) {
      myModel.finish();
    }
  }

  public static class Cancel extends WizardAction {

    public Cancel(WizardModel model) {
      super("Cancel", model);
      setMnemonic('C');
    }

    public void actionPerformed(ActionEvent e) {
      myModel.cancel();
    }
  }


}