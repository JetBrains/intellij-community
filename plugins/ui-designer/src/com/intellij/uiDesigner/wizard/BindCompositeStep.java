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
package com.intellij.uiDesigner.wizard;

import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.StepAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
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

  public JComponent getComponent() {
    return myCardHolder;
  }

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

  public void _commit(boolean finishChosen) throws CommitStepException {
    if(myData.myBindToNewBean){
      myBindToNewBeanStep._commit(finishChosen);
    }
    else{
      myBindToExistingBeanStep._commit(finishChosen);
    }
  }
}
