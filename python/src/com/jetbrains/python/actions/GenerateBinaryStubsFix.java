package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportReferenceImpl;
import com.jetbrains.python.sdk.IronPythonSdkFlavor;
import com.jetbrains.python.sdk.PySkeletonRefresher;
import com.jetbrains.python.sdk.PythonSdkFlavor;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class GenerateBinaryStubsFix implements LocalQuickFix {
  private String myQualifiedName;
  private Sdk mySdk;

  public GenerateBinaryStubsFix(PsiReference reference) {
    myQualifiedName = getReferenceText(reference);
    mySdk = getPythonSdk(reference);
  }

  private static String getReferenceText(PsiReference reference) {
    PsiElement element = reference.getElement();
    while (element.getParent() instanceof PyReferenceExpression) {
      element = element.getParent();
    }
    return element.getText();
  }

  @NotNull
  public String getName() {
    return "Generate stubs for binary module " + myQualifiedName;
  }

  @NotNull
  public String getFamilyName() {
    return "GenerateBinaryStubs";
  }

  public void applyFix(@NotNull Project project, @NotNull final ProblemDescriptor descriptor) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        List<String> assemblyRefs = collectAssemblyReferences(descriptor.getPsiElement().getContainingFile());
        final PySkeletonRefresher refresher = new PySkeletonRefresher(mySdk, null, null);
        refresher.generateSkeleton(myQualifiedName, "", assemblyRefs);
        final VirtualFile skeletonDir;
        skeletonDir = LocalFileSystem.getInstance().findFileByPath(refresher.getSkeletonPath());
        if (skeletonDir != null) {
          skeletonDir.refresh(true, true);
        }
      }
    }, "Generating skeletons for binary module", false, project);
  }

  private List<String> collectAssemblyReferences(PsiFile file) {
    if (!(PythonSdkFlavor.getFlavor(mySdk.getHomePath()) instanceof IronPythonSdkFlavor)) {
      return Collections.emptyList();
    }
    final List<String> result = new ArrayList<String>();
    file.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyCallExpression(PyCallExpression node) {
        super.visitPyCallExpression(node);
        if (node.isCalleeText("AddReference", "AddReferenceByPartialName")) {
          final PyExpression[] args = node.getArguments();
          if (args.length == 1 && args [0] instanceof PyStringLiteralExpression) {
            result.add(((PyStringLiteralExpression) args [0]).getStringValue());
          }
        }
      }
    });
    return result;
  }

  public static boolean isApplicable(PsiReference ref) {
    if (!(ref instanceof PyImportReferenceImpl)) {
      return false;
    }
    final Sdk sdk = getPythonSdk(ref);
    if (sdk == null) {
      return false;
    }
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk.getHomePath());
    if (flavor instanceof IronPythonSdkFlavor) {
      return getReferenceText(ref).matches("[A-Z][A-Za-z0-9]+(\\.[A-Z][A-Za-z0-9]+)*");
    }
    return false;
  }

  @Nullable
  private static Sdk getPythonSdk(PsiReference ref) {
    final Module module = ModuleUtil.findModuleForPsiElement(ref.getElement());
    return module == null ? null : PythonSdkType.findPythonSdk(module);
  }
}
