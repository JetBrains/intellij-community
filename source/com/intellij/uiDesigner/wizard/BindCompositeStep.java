package com.intellij.uiDesigner.wizard;

import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class BindCompositeStep extends StepAdapter{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.wizard.BindCompositeStep");
  private final WizardData myData;
  private final JPanel myCardHolder;

  private final BindToNewBeanStep myBindToNewBeanStep;
  private final BindToExistingBeanStep myBindToExistingBeanStep;

  BindCompositeStep(final WizardData data) {
    LOG.assertTrue(data != null);
    myData = data;

    myBindToNewBeanStep = new BindToNewBeanStep(data);
    myBindToExistingBeanStep = new BindToExistingBeanStep(data);

    myCardHolder = new JPanel(new CardLayout());
    myCardHolder.add(myBindToNewBeanStep.getComponent(), "newBean");
    myCardHolder.add(myBindToExistingBeanStep.getComponent(), "existingBean");
  }

  public JComponent getComponent() {
    return myCardHolder;
  }

  public void _init() {
    if(myData.myBindToNewBean){
      myBindToNewBeanStep._init();
      final CardLayout layout = (CardLayout)myCardHolder.getLayout();
      layout.show(myCardHolder, "newBean");
    }
    else{
      myBindToExistingBeanStep._init();
      final CardLayout layout = (CardLayout)myCardHolder.getLayout();
      layout.show(myCardHolder, "existingBean");
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
