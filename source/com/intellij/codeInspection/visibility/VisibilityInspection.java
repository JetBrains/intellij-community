/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 21, 2001
 * Time: 8:46:41 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.visibility;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.util.XMLExportUtl;
import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.j2ee.j2eeDom.ejb.Ejb;
import com.intellij.j2ee.j2eeDom.ejb.EjbModel;
import com.intellij.j2ee.j2eeDom.ejb.EntityBean;
import com.intellij.j2ee.j2eeDom.xmlData.ObjectsList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class VisibilityInspection extends FilteringInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.visibility.VisibilityInspection");
  public boolean SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
  public boolean SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
  public boolean SUGGEST_PRIVATE_FOR_INNERS = false;
  private WeakerAccessFilter myFilter;
  private QuickFixAction[] myQuickFixActions;
  public static final String DISPLAY_NAME = "Declaration access can be weaker";
  private VisibilityPageComposer myComposer;
  public static final String SHORT_NAME = "WeakerAccess";

  public VisibilityInspection() {
    myQuickFixActions = new QuickFixAction[]{new AcceptSuggestedAccess()};
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox myPackageLocalForMembersCheckbox;
    private final JCheckBox myPrivateForInnersCheckbox;
    private JCheckBox myPackageLocalForTopClassesCheckbox;

    private OptionsPanel() {
      super(new GridBagLayout());

      GridBagConstraints gc = new GridBagConstraints();
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.weightx = 1;
      gc.weighty = 0;
      gc.anchor = GridBagConstraints.NORTHWEST;

      myPackageLocalForMembersCheckbox = new JCheckBox("Suggest package local visibility level for class members");
      myPackageLocalForMembersCheckbox.setSelected(SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS);
      myPackageLocalForMembersCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          boolean selected = myPackageLocalForMembersCheckbox.isSelected();
          SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = selected;
        }
      });

      gc.gridy = 0;
      add(myPackageLocalForMembersCheckbox, gc);

      myPackageLocalForTopClassesCheckbox = new JCheckBox("Suggest package local visibility level for top-level classes");
      myPackageLocalForTopClassesCheckbox.setSelected(SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES);
      myPackageLocalForTopClassesCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          boolean selected = myPackageLocalForTopClassesCheckbox.isSelected();
          SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = selected;
        }
      });

      gc.gridy = 1;
      add(myPackageLocalForTopClassesCheckbox, gc);


      myPrivateForInnersCheckbox = new JCheckBox("Suggest private for inner class members when referenced from outer class only");
      myPrivateForInnersCheckbox.setSelected(SUGGEST_PRIVATE_FOR_INNERS);
      myPrivateForInnersCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          boolean selected = myPrivateForInnersCheckbox.isSelected();
          SUGGEST_PRIVATE_FOR_INNERS = selected;
        }
      });

      gc.gridy = 2;
      gc.weighty = 1;
      add(myPrivateForInnersCheckbox, gc);
    }
  }

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
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

  public WeakerAccessFilter getFilter() {
    return myFilter;
  }

  public void runInspection(AnalysisScope scope) {
    getRefManager().findAllDeclarations(); // Find all declaration elements.

    myFilter = new WeakerAccessFilter(isPackageLocalForMembersShouldBeSuggested(),
                                      isPackageLocalForTopClassesShouldBeSuggested(),
                                      isPrivateForInnersShouldBeSuggested());

    SmartRefElementPointer[] entryPoints = EntryPointsManager.getInstance(getManager().getProject()).getEntryPoints();
    for (int i = 0; i < entryPoints.length; i++) {
      RefElement refElement = entryPoints[i].getRefElement();
      if (refElement != null) {
        myFilter.addIgnoreList(refElement);
      }
    }

    EjbModel[] ejbRootDescriptors = EjbUtil.getEjbModels(getManager().getProject());
    for (int i = 0; i < ejbRootDescriptors.length; i++) {
      EjbModel ejbRootDescriptor = ejbRootDescriptors[i];
      ejbRootDescriptor.getEjbs().visitAllElements(new ObjectsList.ElementVisitor<Ejb>() {
        public void acceptElement(Ejb ejb) {
          if (ejb instanceof EntityBean) {
            EntityBean entityBean = (EntityBean)ejb;
            PsiClass primaryKeyClass = entityBean.getPrimaryKeyClass().getPsiClass();
            if (primaryKeyClass != null) {
              PsiField[] fields = primaryKeyClass.getFields();
              for (int k = 0; k < fields.length; k++) {
                PsiField field = fields[k];
                RefField refField = (RefField)getRefManager().getReference(field);
                if (refField != null) {
                  myFilter.addIgnoreList(refField);
                }
              }

              PsiMethod[] constructors = primaryKeyClass.getConstructors();
              for (int k = 0; k < constructors.length; k++) {
                PsiMethod constructor = constructors[k];
                if (constructor.getParameterList().getParameters().length == 0) {
                  RefMethod refConstructor = (RefMethod)getRefManager().getReference(constructor);
                  if (refConstructor != null) {
                    myFilter.addIgnoreList(refConstructor);
                  }
                }
              }
            }
          }
        }
      });
    }
  }

  public boolean queryExternalUsagesRequests() {
    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (myFilter.accepts(refElement)) {
          refElement.accept(new RefVisitor() {
            public void visitField(final RefField refField) {
              if (refField.getAccessModifier() != PsiModifier.PRIVATE) {
                getManager().enqueueFieldUsagesProcessor(refField, new InspectionManagerEx.UsagesProcessor() {
                  public boolean process(PsiReference psiReference) {
                    myFilter.addIgnoreList(refField);
                    return false;
                  }
                });
              }
            }

            public void visitMethod(final RefMethod refMethod) {
              if (refMethod.isAppMain()) {
                myFilter.addIgnoreList(refMethod);
              }
              else if (!refMethod.isLibraryOverride() && refMethod.getAccessModifier() != PsiModifier.PRIVATE &&
                       !(refMethod instanceof RefImplicitConstructor)) {
                getManager().enqueueDerivedMethodsProcessing(refMethod, new InspectionManagerEx.DerivedMethodsProcessor() {
                  public boolean process(PsiMethod derivedMethod) {
                    myFilter.addIgnoreList(refMethod);
                    return false;
                  }
                });

                getManager().enqueueMethodUsagesProcessor(refMethod, new InspectionManagerEx.UsagesProcessor() {
                  public boolean process(PsiReference psiReference) {
                    myFilter.addIgnoreList(refMethod);
                    return false;
                  }
                });
              }
            }

            public void visitClass(final RefClass refClass) {
              if (!refClass.isAnonymous()) {
                getManager().enqueueDerivedClassesProcessing(refClass, new InspectionManagerEx.DerivedClassesProcessor() {
                  public boolean process(PsiClass inheritor) {
                    myFilter.addIgnoreList(refClass);
                    return false;
                  }
                });

                getManager().enqueueClassUsagesProcessing(refClass, new InspectionManagerEx.UsagesProcessor() {
                  public boolean process(PsiReference psiReference) {
                    myFilter.addIgnoreList(refClass);
                    return false;
                  }
                });
              }
            }
          });
        }
      }
    });

    return false;
  }

  public void exportResults(final Element parentNode) {
    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (myFilter.accepts(refElement)) {
          Element element = XMLExportUtl.createElement(refElement, parentNode, -1);
          Element problemClassElement = new Element("problem_class");
          problemClassElement.addContent("access modifier can be weaker");
          element.addContent(problemClassElement);
          Element descriptionElement = new Element("description");
          String possibleAccess = myFilter.getPossibleAccess(refElement);
          descriptionElement.addContent("can be " + (possibleAccess == PsiModifier.PACKAGE_LOCAL ? "package local" : possibleAccess));
          element.addContent(descriptionElement);
        }
      }
    });
  }

  public QuickFixAction[] getQuickFixes() {
    return myQuickFixActions;
  }

  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[]{InspectionManagerEx.BUILD_GRAPH, InspectionManagerEx.FIND_EXTERNAL_USAGES};
  }

  private boolean isPackageLocalForMembersShouldBeSuggested() {
    return SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS;
  }

  private boolean isPackageLocalForTopClassesShouldBeSuggested() {
    return SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES;
  }

  private boolean isPrivateForInnersShouldBeSuggested() {
    return SUGGEST_PRIVATE_FOR_INNERS;
  }

  private void changeAccessLevel(PsiModifierListOwner psiElement, RefElement refElement, String newAccess) {
    try {
      if (psiElement instanceof PsiVariable) {
        ((PsiVariable)psiElement).normalizeDeclaration();
      }

      PsiModifierList list = psiElement.getModifierList();

      if (psiElement instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod)psiElement;
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass != null && containingClass.getParent() instanceof PsiFile &&
            newAccess == PsiModifier.PRIVATE &&
            list.hasModifierProperty(PsiModifier.FINAL)) {
          list.setModifierProperty(PsiModifier.FINAL, false);
        }
      }

      list.setModifierProperty(newAccess, true);
      refElement.setAccessModifier(newAccess);
      myFilter.addIgnoreList(refElement);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public void cleanup() {
    super.cleanup();
    if (getFilter() != null) {
      getFilter().cleanup();
    }
  }

  public HTMLComposer getComposer() {
    if (myComposer == null) {
      myComposer = new VisibilityPageComposer(myFilter, this);
    }
    return myComposer;
  }

  private class AcceptSuggestedAccess extends QuickFixAction {
    private AcceptSuggestedAccess() {
      super("Accept Suggested Access Level", VisibilityInspection.this);
    }

    protected boolean applyFix(RefElement[] refElements) {
      for (int i = 0; i < refElements.length; i++) {
        RefElement refElement = refElements[i];
        PsiModifierListOwner psiElement = refElement.getElement();
        if (psiElement == null) continue;
        String accessLevel = getFilter().getPossibleAccess(refElement);
        changeAccessLevel(psiElement, refElement, accessLevel);
      }

      return true;
    }
  }
}
