package com.intellij.codeInspection.deadCode;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.util.RefFilter;
import org.jdom.Element;

/**
 * @author max
 */
public class DummyEntryPointsTool extends FilteringInspectionTool {
  private RefEntryPointFilter myFilter;
  private DeadCodeInspection myOwner;
  private QuickFixAction[] myQuickFixActions;

  public DummyEntryPointsTool(DeadCodeInspection owner) {
    myOwner = owner;
  }

  public RefFilter getFilter() {
    if (myFilter == null) {
      myFilter = new RefEntryPointFilter();
    }
    return myFilter;
  }

  public void runInspection(AnalysisScope scope) {}

  public void exportResults(Element parentNode) {}

  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[0];
  }

  public String getDisplayName() {
    return "Entry Points";
  }

  public String getGroupDisplayName() {
    return "";
  }

  public String getShortName() {
    return "";
  }

  public HTMLComposer getComposer() {
    return new DeadHTMLComposer(this);
  }

  public InspectionManagerEx getManager() {
    return myOwner.getManager();
  }

  public QuickFixAction[] getQuickFixes() {
    if (myQuickFixActions == null) {
      myQuickFixActions = new QuickFixAction[]{new MoveEntriesToSuspicious()};
    }
    return myQuickFixActions;
  }

  private class MoveEntriesToSuspicious extends QuickFixAction {
    private MoveEntriesToSuspicious() {
      super("Remove from Entry Points", null, null, DummyEntryPointsTool.this);
    }

    protected boolean applyFix(RefElement[] refElements) {
      for (int i = 0; i < refElements.length; i++) {
        RefElement refElement = refElements[i];
        EntryPointsManager.getInstance(getManager().getProject()).removeEntryPoint(refElement);
      }

      return true;
    }
  }
}
