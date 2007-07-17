/*
 * User: anna
 * Date: 08-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StepSequence {
  private List<ModuleWizardStep> myCommonSteps = new ArrayList<ModuleWizardStep>();
  private Map<String, StepSequence> mySpecificSteps = new HashMap<String, StepSequence>();
  private String myType;
  private StepSequence myParentSequence;

  public StepSequence() {
    this(null);
  }

  public StepSequence(final StepSequence stepSequence) {
    myParentSequence = stepSequence;
  }

  public void addCommonStep(ModuleWizardStep step){
    myCommonSteps.add(step);
  }

  public void addSpecificSteps(String type, StepSequence sequence){
    mySpecificSteps.put(type, sequence);
  }

  public List<ModuleWizardStep> getCommonSteps() {
    return myCommonSteps;
  }

  public StepSequence getSpecificSteps(String type) {
    return mySpecificSteps.get(type);
  }

  public Set<String> getTypes() {
    return mySpecificSteps.keySet();
  }

  @Nullable
  public ModuleWizardStep getNextStep(ModuleWizardStep step) {
    final StepSequence stepSequence = mySpecificSteps.get(myType);
    if (myCommonSteps.contains(step)) {
      final int idx = myCommonSteps.indexOf(step);
      if (idx < myCommonSteps.size() - 1) {
        return myCommonSteps.get(idx + 1);
      }
      if (stepSequence != null && stepSequence.getCommonSteps().size() > 0) {
        return stepSequence.getCommonSteps().get(0);
      }
    }
    if (stepSequence != null) {
      return stepSequence.getNextStep(step);
    }
    return null;
  }

  @Nullable
  public ModuleWizardStep getPreviousStep(ModuleWizardStep step) {
    if (myCommonSteps.contains(step)) {
      final int idx = myCommonSteps.indexOf(step);
      if (idx > 0) {
        return myCommonSteps.get(idx - 1);
      }
      if (myParentSequence != null) {
        final List<ModuleWizardStep> commonSteps = myParentSequence.getCommonSteps();
        if (!commonSteps.isEmpty()) {
          return commonSteps.get(commonSteps.size() - 1);
        }
      }
    }
    final StepSequence stepSequence = mySpecificSteps.get(myType);
    return stepSequence != null ? stepSequence.getPreviousStep(step) : null;
  }

  public void setType(final String type) {
    myType = type;
  }

  public String getSelectedType() {
    return myType;
  }
}