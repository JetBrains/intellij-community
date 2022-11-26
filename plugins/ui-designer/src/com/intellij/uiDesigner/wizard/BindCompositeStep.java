// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.wizard;

import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.StepAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

final class BindCompositeStep extends StepAdapter{
  private final WizardData myData;
  private final JPanel myCardHolder;

  private final BindToNewBeanStep myBindToNewBeanStep;
  private final BindToExistingBeanStep myBindToExistingBeanStep;

  @NonNls
  private static final String CARD_NEW_BEAN = "newBean";

  @NonNls
  private static final String CARD_EXISTING_BEAN = "existingBean";

  BindCompositeStep(@NotNull final WizardData data) {
    myData = data;

    myBindToNewBeanStep = new BindToNewBeanStep(data);
    myBindToExistingBeanStep = new BindToExistingBeanStep(data);

    myCardHolder = new JPanel(new CardLayout());
    myCardHolder.add(myBindToNewBeanStep.getComponent(), CARD_NEW_BEAN);
    myCardHolder.add(myBindToExistingBeanStep.getComponent(), CARD_EXISTING_BEAN);
  }

  @Override
  public JComponent getComponent() {
    return myCardHolder;
  }

  @Override
  public void _init() {
    if(myData.myBindToNewBean){
      myBindToNewBeanStep._init();
      final CardLayout layout = (CardLayout)myCardHolder.getLayout();
      layout.show(myCardHolder, CARD_NEW_BEAN);
    }
    else{
      myBindToExistingBeanStep._init();
      final CardLayout layout = (CardLayout)myCardHolder.getLayout();
      layout.show(myCardHolder, CARD_EXISTING_BEAN);
    }
  }

  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {
    if(myData.myBindToNewBean){
      myBindToNewBeanStep._commit(finishChosen);
    }
    else{
      myBindToExistingBeanStep._commit(finishChosen);
    }
  }
}
