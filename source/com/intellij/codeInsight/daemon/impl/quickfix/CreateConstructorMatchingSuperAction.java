package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;



/**
 * @author ven
 */
public class CreateConstructorMatchingSuperAction extends BaseIntentionAction {
  public CreateConstructorMatchingSuperAction(PsiClass aClass) {
    myClass = aClass;
  }

  private PsiClass myClass;
  private Logger LOG = Logger.getInstance("com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorMatchingSuperAction");

  public String getFamilyName() {
    return "Create constructor matching super";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!myClass.isValid() || !myClass.getManager().isInProject(myClass) || !myClass.isWritable()) return false;
    setText("Create constructor matching super");
    return true;
  }

  public void invoke(final Project project, final Editor editor, PsiFile file) {
    PsiClass baseClass = myClass.getSuperClass();
    PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, myClass, PsiSubstitutor.EMPTY);
    LOG.assertTrue(baseClass != null && substitutor != null);
    List<CandidateInfo> baseConstructors = new ArrayList<CandidateInfo>();
    PsiMethod[] baseConstrs = baseClass.getConstructors();
    for (int i = 0; i < baseConstrs.length; i++) {
      PsiMethod baseConstr = baseConstrs[i];
      if (PsiUtil.isAccessible(baseConstr, myClass, myClass)) baseConstructors.add(new CandidateInfo(baseConstr, substitutor));
    }

    CandidateInfo[] constructors = baseConstructors.toArray(new CandidateInfo[baseConstructors.size()]);
    if (constructors.length == 0) {
      constructors = new CandidateInfo[baseConstrs.length];
      for (int i = 0; i < baseConstrs.length; i++) {
        constructors[i] = new CandidateInfo(baseConstrs[i], substitutor);
      }
    }

    LOG.assertTrue(constructors.length >=1); // Otherwise we won't have been messing with all this stuff
    if (constructors.length > 1) {
      MemberChooser chooser = new MemberChooser(constructors, false, true, project);
      chooser.setTitle("Choose Super Class Constructors");
      chooser.show();
      if (chooser.getExitCode() != MemberChooser.OK_EXIT_CODE) return;
      constructors = (CandidateInfo[]) chooser.getSelectedElements(new CandidateInfo[0]);
    }

    final CandidateInfo[] constructors1 = constructors;
    ApplicationManager.getApplication().runWriteAction (
      new Runnable() {
        public void run() {
          try {
            PsiElementFactory factory = myClass.getManager().getElementFactory();
            CodeStyleManager reformatter = CodeStyleManager.getInstance(project);
            PsiMethod derived = null;
            for (int i = 0; i < constructors1.length; i++) {
              CandidateInfo candidate = constructors1[i];
              PsiMethod base = (PsiMethod) candidate.getElement();
              derived = GenerateMembersUtil.substituteGenericMethod(base, candidate.getSubstitutor());
              derived.getNameIdentifier().replace(myClass.getNameIdentifier());
              StringBuffer buffer = new StringBuffer();
              buffer.append("void foo () {\nsuper(");

              PsiParameter[] params = derived.getParameterList().getParameters();
              for (int j = 0; j < params.length; j++) {
                PsiParameter param = params[j];
                buffer.append(param.getName());
                if (j < params.length - 1) buffer.append(",");
              }
              buffer.append(");\n}");
              PsiMethod stub = factory.createMethodFromText(buffer.toString(), myClass);

              derived.getBody().replace(stub.getBody());
              derived = (PsiMethod) reformatter.reformat(derived);
              derived = (PsiMethod) myClass.add(derived);
            }
            if (derived != null) {
              editor.getCaretModel().moveToOffset(derived.getTextRange().getStartOffset());
              editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }

          QuickFixAction.spoilDocument(project, myClass.getContainingFile());
        }
      }
    );
  }

  public boolean startInWriteAction() {
    return false;
  }
}
