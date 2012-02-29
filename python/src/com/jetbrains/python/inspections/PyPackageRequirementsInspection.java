package com.jetbrains.python.inspections;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.Function;
import com.jetbrains.python.codeInsight.stdlib.PyStdlibUtil;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author vlan
 */
public class PyPackageRequirementsInspection extends PyInspection {
  public JDOMExternalizableStringList ignoredPackages = new JDOMExternalizableStringList();

  @NotNull
  @Override
  public String getDisplayName() {
    return "Package requirements";
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListEditForm form = new ListEditForm("Ignore packages", ignoredPackages);
    return form.getContentPanel();
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session, ignoredPackages);
  }

  @Nullable
  public static PyPackageRequirementsInspection getInstance(@NotNull PsiElement element) {
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(element.getProject()).getInspectionProfile();
    final String toolName = PyPackageRequirementsInspection.class.getSimpleName();
    final InspectionProfileEntry inspectionTool = inspectionProfile.getInspectionTool(toolName, element);
    if (inspectionTool instanceof LocalInspectionToolWrapper) {
      final LocalInspectionToolWrapper profileEntry = (LocalInspectionToolWrapper)inspectionTool;
      final LocalInspectionTool tool = profileEntry.getTool();
      if (tool instanceof PyPackageRequirementsInspection) {
        return (PyPackageRequirementsInspection)tool;
      }
    }
    return null;
  }

  private static class Visitor extends PyInspectionVisitor {
    private final Set<String> myIgnoredPackages;

    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session, Collection<String> ignoredPackages) {
      super(holder, session);
      myIgnoredPackages = ImmutableSet.copyOf(ignoredPackages);
    }

    @Override
    public void visitPyFile(PyFile node) {
      final Module module = ModuleUtil.findModuleForPsiElement(node);
      if (module != null) {
        if (isRunningPackagingTasks(module)) {
          return;
        }
        final Sdk sdk = PythonSdkType.findPythonSdk(module);
        if (sdk != null) {
          final List<PyRequirement> unsatisfied = findUnsatisfiedRequirements(module, sdk);
          if (unsatisfied != null && !unsatisfied.isEmpty()) {
            final boolean plural = unsatisfied.size() > 1;
            String msg = String.format("Package requirement%s %s %s not satisfied",
                                       plural ? "s" : "",
                                       requirementsToString(unsatisfied),
                                       plural ? "are" : "is");
            registerProblem(node, msg, new InstallRequirementsFix(null, module, sdk, unsatisfied));
          }
        }
      }
    }

    @Override
    public void visitPyFromImportStatement(PyFromImportStatement node) {
      final PyReferenceExpression expr = node.getImportSource();
      if (expr != null) {
        checkPackageNameInRequirements(expr);
      }
    }

    @Override
    public void visitPyImportStatement(PyImportStatement node) {
      for (PyImportElement element : node.getImportElements()) {
        final PyReferenceExpression expr = element.getImportReferenceExpression();
        if (expr != null) {
          checkPackageNameInRequirements(expr);
        }
      }
    }

    private void checkPackageNameInRequirements(@NotNull PyQualifiedExpression importedExpression) {
      final List<PyExpression> expressions = PyResolveUtil.unwindQualifiers(importedExpression);
      if (!expressions.isEmpty()) {
        final PyExpression packageReferenceExpression = expressions.get(0);
        final String packageName = packageReferenceExpression.getName();
        if (packageName != null && !myIgnoredPackages.contains(packageName)) {
          final Collection<String> stdlibPackages = PyStdlibUtil.getPackages();
          if (stdlibPackages != null) {
            for (String name : stdlibPackages) {
              if (packageName.equals(name)) {
                return;
              }
            }
          }
          final Module module = ModuleUtil.findModuleForPsiElement(packageReferenceExpression);
          if (module != null) {
            final List<PyRequirement> requirements = PyPackageManager.getRequirements(module);
            if (requirements != null) {
              for (PyRequirement req : requirements) {
                if (packageName.equalsIgnoreCase(req.getName())) {
                  return;
                }
              }
              final PsiReference reference = packageReferenceExpression.getReference();
              if (reference != null) {
                final PsiElement element = reference.resolve();
                if (element != null) {
                  final PsiFile file = element.getContainingFile();
                  final VirtualFile virtualFile = file.getVirtualFile();
                  if (ModuleUtil.moduleContainsFile(module, virtualFile, false)) {
                    return;
                  }
                }
              }
              registerProblem(packageReferenceExpression, String.format("Package '%s' is not listed in project requirements", packageName),
                              ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null,
                              new AddToRequirementsFix(module, packageName, LanguageLevel.forElement(importedExpression)),
                              new IgnoreRequirementFix(packageName));
            }
          }
        }
      }
    }
  }

  @NotNull
  private static String requirementsToString(@NotNull List<PyRequirement> requirements) {
    return StringUtil.join(requirements, new Function<PyRequirement, String>() {
      @Override
      public String fun(PyRequirement requirement) {
        return String.format("'%s'", requirement.toString());
      }
    }, ", ");
  }

  @Nullable
  private static List<PyRequirement> findUnsatisfiedRequirements(@NotNull Module module, @NotNull Sdk sdk) {
    final PyPackageManager manager = PyPackageManager.getInstance(sdk);
    List<PyRequirement> requirements = PyPackageManager.getRequirements(module);
    if (requirements != null) {
      final List<PyPackage> packages;
      try {
        packages = manager.getPackages();
      }
      catch (PyExternalProcessException ignored) {
        return null;
      }
      final List<PyRequirement> unsatisfied = new ArrayList<PyRequirement>();
      for (PyRequirement req : requirements) {
        if (!req.match(packages)) {
          unsatisfied.add(req);
        }
      }
      return unsatisfied;
    }
    return null;
  }

  private static void setRunningPackagingTasks(@NotNull Module module, boolean value) {
    module.putUserData(PyPackageManager.RUNNING_PACKAGING_TASKS, value);
  }

  private static boolean isRunningPackagingTasks(@NotNull Module module) {
    final Boolean value = module.getUserData(PyPackageManager.RUNNING_PACKAGING_TASKS);
    return value != null && value;
  }

  public static class InstallRequirementsFix implements LocalQuickFix {
    @NotNull private String myName;
    @NotNull private final Module myModule;
    @NotNull private Sdk mySdk;
    @NotNull private final List<PyRequirement> myUnsatisfied;

    public InstallRequirementsFix(@Nullable String name, @NotNull Module module, @NotNull Sdk sdk,
                                  @NotNull List<PyRequirement> unsatisfied) {
      myName = name != null ? name : "Install requirements";
      myModule = module;
      mySdk = sdk;
      myUnsatisfied = unsatisfied;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return myName;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      final PyPackageManager.UI ui = new PyPackageManager.UI(project, mySdk, new PyPackageManager.UI.Listener() {
        @Override
        public void started() {
          setRunningPackagingTasks(myModule, true);
        }

        @Override
        public void finished(@Nullable PyExternalProcessException exception) {
          setRunningPackagingTasks(myModule, false);
        }
      });
      ui.install(myUnsatisfied, Collections.<String>emptyList());
    }
  }

  private static class IgnoreRequirementFix implements LocalQuickFix {
    @NotNull private final String myPackageName;

    public IgnoreRequirementFix(@NotNull String packageName) {
      myPackageName = packageName;
    }

    @NotNull
    @Override
    public String getName() {
      return String.format("Ignore package requirement '%s'", myPackageName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element != null) {
        final PyPackageRequirementsInspection inspection = PyPackageRequirementsInspection.getInstance(element);
        if (inspection != null) {
          final JDOMExternalizableStringList ignoredPackages = inspection.ignoredPackages;
          if (!ignoredPackages.contains(myPackageName)) {
            ignoredPackages.add(myPackageName);
            final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
            InspectionProfileManager.getInstance().fireProfileChanged(profile);
          }
        }
      }
    }
  }

  private static class AddToRequirementsFix implements LocalQuickFix {
    @Nullable private final PyListLiteralExpression mySetupPyRequires;
    @Nullable private final Document myRequirementsTxt;
    @Nullable private final PyArgumentList mySetupArgumentList;
    @NotNull private final String myPackageName;
    private final LanguageLevel myLanguageLevel;

    private AddToRequirementsFix(@NotNull Module module, @NotNull String packageName, LanguageLevel languageLevel) {
      myPackageName = packageName;
      myLanguageLevel = languageLevel;
      myRequirementsTxt = PyPackageUtil.findRequirementsTxt(module);
      mySetupPyRequires = PyPackageUtil.findSetupPyRequires(module);
      final PyFile setupPy = PyPackageUtil.findSetupPy(module);
      if (setupPy != null) {
        final PyCallExpression setupCall = PyPackageUtil.findSetupCall(setupPy);
        if (setupCall != null) {
          mySetupArgumentList = setupCall.getArgumentList();
        }
        else {
          mySetupArgumentList = null;
        }
      }
      else {
        mySetupArgumentList = null;
      }
    }

    @NotNull
    @Override
    public String getName() {
      final String target;
      if (myRequirementsTxt != null) {
        target = "requirements.txt";
      }
      else if (mySetupPyRequires != null || mySetupArgumentList != null) {
        target = "setup.py";
      }
      else {
        target = "project requirements";
      }
      return String.format("Add requirement '%s' to %s", myPackageName, target);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              if (myRequirementsTxt != null) {
                if (myRequirementsTxt.isWritable()) {
                  myRequirementsTxt.insertString(0, myPackageName + "\n");
                }
              }
              else {
                final PyElementGenerator generator = PyElementGenerator.getInstance(project);
                if (mySetupPyRequires != null) {
                  if (mySetupPyRequires.getContainingFile().isWritable()) {
                    final PyStringLiteralExpression literal = generator.createStringLiteralFromString(myPackageName);
                    mySetupPyRequires.add(literal);
                  }
                }
                else if (mySetupArgumentList != null) {
                  final PyKeywordArgument requiresArg = generateRequiresKwarg(generator);
                  if (requiresArg != null) {
                    mySetupArgumentList.addArgument(requiresArg);
                  }
                }
              }
            }

            @Nullable
            private PyKeywordArgument generateRequiresKwarg(PyElementGenerator generator) {
              final String text = String.format("foo(requires=[\"%s\"])", myPackageName);
              final PyExpression generated = generator.createExpressionFromText(myLanguageLevel, text);
              PyKeywordArgument installRequiresArg = null;
              if (generated instanceof PyCallExpression) {
                final PyCallExpression foo = (PyCallExpression)generated;
                for (PyExpression arg : foo.getArguments()) {
                  if (arg instanceof PyKeywordArgument) {
                    final PyKeywordArgument kwarg = (PyKeywordArgument)arg;
                    if ("requires".equals(kwarg.getKeyword())) {
                      installRequiresArg = kwarg;
                    }
                  }
                }
              }
              return installRequiresArg;
            }
          });
        }
      }, getName(), null);
    }
  }
}
