package com.intellij.testIntegration.createTest;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightNamesUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.testIntegration.CreateTestProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreateTestAction extends PsiElementBaseIntentionAction {
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.create.test");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
    if (element == null) return false;

    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass == null ||
        psiClass.isAnnotationType() ||
        psiClass.isInterface() ||
        psiClass.isEnum() ||
        psiClass instanceof PsiAnonymousClass ||
        PsiTreeUtil.getParentOfType(psiClass, PsiClass.class) != null || // inner
        isUnderTestSources(psiClass)) {
      return false;
    }

    PsiJavaToken leftBrace = psiClass.getLBrace();
    if (leftBrace == null) return false;
    if (element.getTextOffset() >= leftBrace.getTextOffset()) return false;

    TextRange declarationRange = HighlightNamesUtil.getClassDeclarationTextRange(psiClass);
    if (!declarationRange.contains(element.getTextRange())) return false;

    return true;
  }

  private boolean isUnderTestSources(PsiClass c) {
    ProjectRootManager rm = ProjectRootManager.getInstance(c.getProject());
    VirtualFile f = c.getContainingFile().getVirtualFile();
    if (f == null) return false;
    return rm.getFileIndex().isInTestSourceContent(f);
  }

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());

    final Module srcModule = ModuleUtil.findModuleForPsiElement(file);
    final PsiClass srcClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    PsiDirectory srcDir = file.getContainingDirectory();
    PsiPackage srcPackage = JavaDirectoryService.getInstance().getPackage(srcDir);

    final CreateTestDialog d = new CreateTestDialog(project,
                                                    getText(),
                                                    srcClass,
                                                    srcPackage,
                                                    srcModule);
    d.show();
    if (!d.isOK()) return;

    PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

              PsiClass targetClass = JavaDirectoryService.getInstance().createClass(d.getTargetDirectory(), d.getClassName());
              addSuperClass(targetClass, project, d.getSuperClassName());
              addTestMethods(targetClass,
                             d.getSelectedTestProvider(),
                             d.getSelectedMethods(),
                             d.shouldGeneratedBefore(),
                             d.shouldGeneratedAfter());

              CodeInsightUtil.positionCursor(project, targetClass.getContainingFile(), targetClass.getLBrace());
            }
            catch (IncorrectOperationException e) {
              showErrorLater(project, d.getClassName());
            }
          }
        });
      }
    });
  }

  private void showErrorLater(final Project project, final String targetClassName) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Messages.showErrorDialog(project,
                                 CodeInsightBundle.message("intention.error.cannot.create.class.message", targetClassName),
                                 CodeInsightBundle.message("intention.error.cannot.create.class.title"));
      }
    });
  }

  private void addSuperClass(PsiClass targetClass, Project project, String superClassName) throws IncorrectOperationException {
    if (superClassName == null) return;

    PsiElementFactory ef = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiJavaCodeReferenceElement superClassRef;

    PsiClass superClass = findClass(project, superClassName);
    if (superClass != null) {
      superClassRef = ef.createClassReferenceElement(superClass);
    }
    else {
      superClassRef = ef.createFQClassNameReferenceElement(superClassName, GlobalSearchScope.allScope(project));
    }
    targetClass.getExtendsList().add(superClassRef);
  }

  private PsiClass findClass(Project project, String fqName) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    return JavaPsiFacade.getInstance(project).findClass(fqName, scope);
  }

  private void addTestMethods(PsiClass targetClass,
                              CreateTestProvider provider,
                              MemberInfo[] methods,
                              boolean generateBefore,
                              boolean generateAfter) throws IncorrectOperationException {
    if (generateBefore) addMethod(targetClass, "setUp", provider.getSetUpAnnotation());
    if (generateAfter) addMethod(targetClass, "tearDown", provider.getTearDownAnnotation());

    for (MemberInfo m : methods) {
      addMethod(targetClass,
                "test" + StringUtil.capitalize(m.getMember().getName()),
                provider.getTestAnnotation());
    }
  }

  private void addMethod(PsiClass targetClass, String name, String annotation) throws IncorrectOperationException {
    PsiElementFactory f = JavaPsiFacade.getInstance(targetClass.getProject()).getElementFactory();
    PsiMethod test = f.createMethod(name, PsiType.VOID);
    test.getBody().add(f.createCommentFromText("// Add your code here", test));

    if (annotation != null) {
      PsiAnnotation a = f.createAnnotationFromText("@" + annotation, test);
      PsiModifierList modifiers = test.getModifierList();
      PsiElement first = modifiers.getFirstChild();
      if (first == null) modifiers.add(a);
      else modifiers.addBefore(a, first);

      JavaCodeStyleManager.getInstance(targetClass.getProject()).shortenClassReferences(modifiers);
    }

    targetClass.add(test);
  }

  public boolean startInWriteAction() {
    return false;
  }
}