package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Alexey.Ivanov
 */
public class PyDeprecatedModulesInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.deprecated.modules");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {
    private static final Map<LanguageLevel, Set<String>> DEPRECATED_MODULES = new HashMap<LanguageLevel, Set<String>>();

    static {
      Set<String> deprecated24 = new HashSet<String>();
      deprecated24.add("whrandom");
      deprecated24.add("rfc822");
      deprecated24.add("mimetools");
      deprecated24.add("MimeWriter");
      deprecated24.add("mimify");
      deprecated24.add("statcashe");
      deprecated24.add("buildtools");
      deprecated24.add("cfmfile");
      deprecated24.add("macfs");
      DEPRECATED_MODULES.put(LanguageLevel.PYTHON24, deprecated24);

      Set<String> deprecated25 = new HashSet<String>();
      deprecated25.addAll(deprecated24);
      deprecated25.remove("whrandom");
      deprecated25.remove("statcashe");
      deprecated25.add("gopherlib");
      deprecated25.add("rgbimg");
      deprecated25.add("multifile");
      deprecated25.add("md5");
      deprecated25.add("sha");
      DEPRECATED_MODULES.put(LanguageLevel.PYTHON25, deprecated25);

      Set<String> deprecated26 = new HashSet<String>();
      deprecated26.addAll(deprecated25);
      deprecated26.remove("macfs");
      deprecated26.remove("gopherlib");
      deprecated26.remove("rgbimg");
      deprecated26.add("sets");
      DEPRECATED_MODULES.put(LanguageLevel.PYTHON26, deprecated26);
    }

    private static LanguageLevel getLanguageLevel(PyElement node) {
      VirtualFile virtualFile = node.getContainingFile().getVirtualFile();
      return (virtualFile != null) ? LanguageLevel.forFile(virtualFile) : LanguageLevel.getDefault();
    }

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyImportStatement(PyImportStatement node) {
      PyImportElement[] importElements = node.getImportElements();
      for (PyImportElement importElement: importElements) {
        PyReferenceExpression importReference = importElement.getImportReference();
        if (importReference != null) {
          String name = importReference.getName();
          Set<String> deprecated = DEPRECATED_MODULES.get(getLanguageLevel(node));
          if (deprecated != null && deprecated.contains(name)) {
            registerProblem(node, PyBundle.message("INSP.module.$0.is.deprecated.in.version", name));
          }
        }
      }
    }
  }
}
