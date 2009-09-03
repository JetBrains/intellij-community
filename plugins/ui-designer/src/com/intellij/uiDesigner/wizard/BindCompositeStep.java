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

  public Icon getIcon() {
    if(myData.myBindToNewBean){
      return myBindToNewBeanStep.getIcon();
    }
    else{
      return myBindToExistingBeanStep.getIcon();
    }
  }
}
