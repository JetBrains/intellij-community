package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author mike
 */
public class GenerateDelegateHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateDelegateHandler");

  public void invoke(final Project project, final Editor editor, final PsiFile file) {
    if (!file.isWritable()) {
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(editor.getDocument(), project)){
        return;
      }
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final PsiElement target = chooseTarget(file, editor, project);
    if (target == null) return;

    final CandidateInfo[] candidates = chooseMethods(target, file, editor, project);
    if (candidates == null || candidates.length == 0) return;


    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          int offset = editor.getCaretModel().getOffset();

          PsiMethod[] prototypes = new PsiMethod[candidates.length];
          for (int i = 0; i < candidates.length; i++) {
            prototypes[i] = generateDelegatePrototype(candidates[i], target);
          }

          Object[] results = GenerateMembersUtil.insertMembersAtOffset(project, editor.getDocument(), file, offset, prototypes);

          PsiMethod firstMethod = (PsiMethod)results[0];
          final PsiCodeBlock block = firstMethod.getBody();
          final PsiElement first = block.getFirstBodyElement();
          LOG.assertTrue(first != null);
          editor.getCaretModel().moveToOffset(first.getTextRange().getStartOffset());
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          editor.getSelectionModel().removeSelection();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }

  private PsiMethod generateDelegatePrototype(CandidateInfo methodCandidate, PsiElement target) throws IncorrectOperationException {
    PsiMethod method = GenerateMembersUtil.substituteGenericMethod((PsiMethod)methodCandidate.getElement(), methodCandidate.getSubstitutor());
    clearMethod(method);

    clearModifiers(method);

    @NonNls StringBuffer call = new StringBuffer();

    PsiModifierList modifierList = null;

    if (method.getReturnType() != PsiType.VOID) {
      call.append("return ");
    }

    if (target instanceof PsiField) {
      PsiField field = (PsiField)target;
      modifierList = field.getModifierList();
      final String name = field.getName();

      final PsiParameter[] parameters = method.getParameterList().getParameters();
      for (PsiParameter parameter : parameters) {
        if (name.equals(parameter.getName())) {
          call.append("this.");
          break;
        }
      }

      call.append(name);
      call.append(".");
    }
    else if (target instanceof PsiMethod) {
      PsiMethod m = (PsiMethod)target;
      modifierList = m.getModifierList();
      call.append(m.getName());
      call.append("().");
    }

    call.append(method.getName());
    call.append("(");
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int j = 0; j < parameters.length; j++) {
      PsiParameter parameter = parameters[j];
      if (j > 0) call.append(",");
      call.append(parameter.getName());
    }
    call.append(");");

    PsiManager psiManager = method.getManager();
    PsiStatement stmt = psiManager.getElementFactory().createStatementFromText(call.toString(), method);
    stmt = (PsiStatement)CodeStyleManager.getInstance(psiManager.getProject()).reformat(stmt);
    method.getBody().add(stmt);

    if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
      method.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
    }

    method.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);

    return method;
  }

  private void clearMethod(PsiMethod method) throws IncorrectOperationException {
    LOG.assertTrue(!method.isPhysical());
    PsiCodeBlock codeBlock = method.getManager().getElementFactory().createCodeBlock();
    if (method.getBody() != null) {
      method.getBody().replace(codeBlock);
    }
    else {
      method.add(codeBlock);
    }

    final PsiDocComment docComment = method.getDocComment();
    if (docComment != null) {
      docComment.delete();
    }
  }

  private void clearModifiers(PsiMethod method) throws IncorrectOperationException {
    final PsiElement[] children = method.getModifierList().getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiKeyword) child.delete();
    }
  }

  private CandidateInfo[] chooseMethods(PsiElement target, PsiFile file, Editor editor, Project project) {
    PsiClassType.ClassResolveResult resolveResult = null;

    if (target instanceof PsiField) {
      resolveResult = PsiUtil.resolveGenericsClassInType(((PsiField)target).getType());
    }
    else if (target instanceof PsiMethod) {
      resolveResult = PsiUtil.resolveGenericsClassInType(((PsiMethod)target).getReturnType());
    }

    if (resolveResult == null || resolveResult.getElement() == null) return null;
    PsiClass targetClass = resolveResult.getElement();
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (aClass == null) return null;

    List<CandidateInfo> methodInstances = new ArrayList<CandidateInfo>();

    final PsiMethod[] allMethods = targetClass.getAllMethods();
    final Set<MethodSignature> signatures = new HashSet<MethodSignature>();
    Map<PsiClass, PsiSubstitutor> superSubstitutors = new HashMap<PsiClass, PsiSubstitutor>();
    PsiManager manager = targetClass.getManager();
    for (PsiMethod method : allMethods) {
      final PsiClass superClass = method.getContainingClass();
      if (superClass.getQualifiedName().equals("java.lang.Object")) continue;
      if (method.isConstructor()) continue;
      PsiSubstitutor superSubstitutor = superSubstitutors.get(superClass);
      if (superSubstitutor == null) {
        superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, targetClass, substitutor);
        superSubstitutors.put(superClass, superSubstitutor);
      }
      PsiSubstitutor methodSubstitutor = GenerateMembersUtil.correctSubstitutor(method, superSubstitutor);
      MethodSignature signature = method.getSignature(methodSubstitutor);
      if (!signatures.contains(signature)) {
        signatures.add(signature);
        if (manager.getResolveHelper().isAccessible(method, target, aClass)) {
          methodInstances
            .add(new CandidateInfo(method, methodSubstitutor));
        }
      }
    }

    CandidateInfo[] result;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      MemberChooser chooser = new MemberChooser(methodInstances.toArray(new Object[methodInstances.size()]), false, true, project);
      chooser.setTitle(CodeInsightBundle.message("generate.delegate.method.chooser.title"));
      chooser.setCopyJavadocVisible(false);
      chooser.show();

      if (chooser.getExitCode() != MemberChooser.OK_EXIT_CODE) return null;

      final Object[] selectedElements = chooser.getSelectedElements();
      result = new CandidateInfo[selectedElements.length];
      System.arraycopy(selectedElements, 0, result, 0, selectedElements.length);
    }
    else {
      result = new CandidateInfo[] {methodInstances.get(0)};
    }

    return result;
  }

  public boolean isApplicable (PsiFile file, Editor editor) {
    PsiMember[] targetElements = getTargetElements(file, editor);
    return targetElements != null && targetElements.length > 0;
  }

  private PsiElement chooseTarget(PsiFile file, Editor editor, Project project) {
    PsiElement target = null;
    final PsiMember[] targetElements = getTargetElements(file, editor);
    if (targetElements == null || targetElements.length == 0) return null;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      MemberChooser chooser = new MemberChooser(targetElements, false, false, project);
      chooser.setTitle(CodeInsightBundle.message("generate.delegate.target.chooser.title"));
      chooser.setCopyJavadocVisible(false);
      chooser.show();

      if (chooser.getExitCode() != MemberChooser.OK_EXIT_CODE) return null;

      final Object[] selectedElements = chooser.getSelectedElements();

      if (selectedElements != null && selectedElements.length > 0) target = (PsiElement)selectedElements[0];
    }
    else {
      target = targetElements[0];
    }
    return target;
  }

  private PsiMember[] getTargetElements(PsiFile file, Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (aClass == null) return null;

    List<PsiMember> result = new ArrayList<PsiMember>();

    final PsiField[] fields = aClass.getAllFields();
    PsiResolveHelper helper = aClass.getManager().getResolveHelper();
    for (PsiField field : fields) {
      final PsiType type = field.getType();
      if (helper.isAccessible(field, aClass, aClass) && type instanceof PsiClassType) {
        result.add(field);
      }
    }

    final PsiMethod[] methods = aClass.getAllMethods();
    for (PsiMethod method : methods) {
      if ("java.lang.Object".equals(method.getContainingClass().getQualifiedName())) continue;
      final PsiType returnType = method.getReturnType();
      if (returnType != null && PropertyUtil.isSimplePropertyGetter(method) && helper.isAccessible(method, aClass, aClass) &&
          returnType instanceof PsiClassType) {
        result.add(method);
      }
    }

    return result.toArray(new PsiMember[result.size()]);
  }

}
