/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 24, 2001
 * Time: 2:46:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.canBeFinal;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.codeInspection.util.XMLExportUtl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class CanBeFinalInspection extends FilteringInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.canBeFinal.CanBeFinalInspection");

  public boolean REPORT_CLASSES = true;
  public boolean REPORT_METHODS = true;
  public boolean REPORT_FIELDS = true;
  private QuickFixAction[] myQuickFixActions;
  public static final String DISPLAY_NAME = "Declaration can have final modifier";
  public static final String SHORT_NAME = "CanBeFinal";
  private CanBeFinalFilter myFilter;
  private CanBeFinalComposer myComposer;

  public CanBeFinalInspection() {
    myQuickFixActions = new QuickFixAction[]{new AcceptSuggested()};
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox myReportClassesCheckbox;
    private final JCheckBox myReportMethodsCheckbox;
    private final JCheckBox myReportFieldsCheckbox;

    private OptionsPanel() {
      super(new GridBagLayout());

      GridBagConstraints gc = new GridBagConstraints();
      gc.weighty = 0;
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;


      myReportClassesCheckbox = new JCheckBox("Report classes");
      myReportClassesCheckbox.setSelected(REPORT_CLASSES);
      myReportClassesCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          boolean selected = myReportClassesCheckbox.isSelected();
          REPORT_CLASSES = selected;
        }
      });
      gc.gridy = 0;
      add(myReportClassesCheckbox, gc);

      myReportMethodsCheckbox = new JCheckBox("Report methods");
      myReportMethodsCheckbox.setSelected(REPORT_METHODS);
      myReportMethodsCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          boolean selected = myReportMethodsCheckbox.isSelected();
          REPORT_METHODS = selected;
        }
      });
      gc.gridy++;
      add(myReportMethodsCheckbox, gc);

      myReportFieldsCheckbox = new JCheckBox("Report fields");
      myReportFieldsCheckbox.setSelected(REPORT_FIELDS);
      myReportFieldsCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          boolean selected = myReportFieldsCheckbox.isSelected();
          REPORT_FIELDS = selected;
        }
      });

      gc.weighty = 1;
      gc.gridy++;
      add(myReportFieldsCheckbox, gc);
    }
  }

  public boolean isReportClasses() {
    return REPORT_CLASSES;
  }

  public boolean isReportMethods() {
    return REPORT_METHODS;
  }

  public boolean isReportFields() {
    return REPORT_FIELDS;
  }

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  public void runInspection(AnalysisScope scope) {
    getRefManager().findAllDeclarations();
  }

  public boolean queryExternalUsagesRequests() {
    final CanBeFinalFilter filter = new CanBeFinalFilter(this);
    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (filter.accepts(refElement)) {
          refElement.accept(new RefVisitor() {
            public void visitMethod(final RefMethod refMethod) {
              if (!refMethod.isStatic() && refMethod.getAccessModifier() != PsiModifier.PRIVATE &&
                  !(refMethod instanceof RefImplicitConstructor)) {
                getManager().enqueueDerivedMethodsProcessing(refMethod, new InspectionManagerEx.DerivedMethodsProcessor() {
                  public boolean process(PsiMethod derivedMethod) {
                    refMethod.setCanBeFinal(false);
                    return false;
                  }
                });
              }
            }

            public void visitClass(final RefClass refClass) {
              if (!refClass.isAnonymous()) {
                getManager().enqueueDerivedClassesProcessing(refClass, new InspectionManagerEx.DerivedClassesProcessor() {
                  public boolean process(PsiClass inheritor) {
                    refClass.setCanBeFinal(false);
                    return false;
                  }
                });
              }
            }

            public void visitField(final RefField refField) {
              getManager().enqueueFieldUsagesProcessor(refField, new InspectionManagerEx.UsagesProcessor() {
                public boolean process(PsiReference psiReference) {
                  PsiElement expression = psiReference.getElement();
                  if (expression instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting((PsiExpression)expression)) {
                    refField.setCanBeFinal(false);
                    return false;
                  }
                  return true;
                }
              });
            }
          });
        }
      }
    });

    return false;
  }

  public RefFilter getFilter() {
    if (myFilter == null) {
      myFilter = new CanBeFinalFilter(this);
    }
    return myFilter;
  }

  public HTMLComposer getComposer() {
    if (myComposer == null) {
      myComposer = new CanBeFinalComposer(this);
    }
    return myComposer;
  }

  public void exportResults(final Element parentNode) {
    final CanBeFinalFilter filter = new CanBeFinalFilter(this);

    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (filter.accepts(refElement)) {
          Element element = XMLExportUtl.createElement(refElement, parentNode, -1);
          Element problemClassElement = new Element("problem_class");
          problemClassElement.addContent("can be final");
          element.addContent(problemClassElement);

          Element descriptionElement = new Element("description");
          descriptionElement.addContent("declaration can have final modifier");
          element.addContent(descriptionElement);
        }
      }
    });
  }

  public QuickFixAction[] getQuickFixes(final RefElement[] refElements) {
    return myQuickFixActions;
  }

  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[]{InspectionManagerEx.BUILD_GRAPH, InspectionManagerEx.FIND_EXTERNAL_USAGES};
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getGroupDisplayName() {
    return "Declaration Redundancy";
  }

  public String getShortName() {
    return SHORT_NAME;
  }

  private static void makeFinal(PsiModifierListOwner psiElement, RefElement refElement) {
    try {
      if (psiElement instanceof PsiVariable) {
        ((PsiVariable)psiElement).normalizeDeclaration();
      }
      psiElement.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    refElement.setIsFinal(true);
  }

  private class AcceptSuggested extends QuickFixAction {
    private AcceptSuggested() {
      super("Accept Suggested Final Modifier", CanBeFinalInspection.this);
    }

    protected boolean applyFix(RefElement[] refElements) {
      for (int i = 0; i < refElements.length; i++) {
        RefElement refElement = refElements[i];
        PsiModifierListOwner psiElement = (PsiModifierListOwner)refElement.getElement();

        if (psiElement == null) continue;
        makeFinal(psiElement, refElement);
      }

      return true;
    }
  }
}
