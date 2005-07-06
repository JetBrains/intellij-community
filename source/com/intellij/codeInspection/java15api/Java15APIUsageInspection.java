package com.intellij.codeInspection.java15api;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class Java15APIUsageInspection extends LocalInspectionTool {
  private static THashSet<String> ourForbiddenAPI = new THashSet<String>(1500);

  static {
    try {
      final InputStream stream = Java15APIUsageInspection.class.getResourceAsStream("apiList.txt");
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));

      do {
        String line = reader.readLine();
        if (line == null) break;

        ourForbiddenAPI.add(line);
      } while(true);
    }
    catch (UnsupportedEncodingException e) {
      // can't be.
    }
    catch (IOException e) {
      // can't be
    }
  }

  public String getGroupDisplayName() {
    return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
  }

  public String getDisplayName() {
    return "Usages of API documented as @since 1.5";
  }

  public String getShortName() {
    return "Since15";
  }


  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly) {
    return checkReferencesIn(method, manager);
  }

  @Override
  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    ProblemDescriptor[] result = null;
    result = merge(result, checkReferencesIn(aClass.getImplementsList(), manager));
    result = merge(result, checkReferencesIn(aClass.getExtendsList(), manager));
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    for (PsiClassInitializer initializer : initializers) {
      result = merge(result, checkReferencesIn(initializer, manager));
    }
    return result;
  }

  @Override
  public ProblemDescriptor[] checkField(PsiField field, InspectionManager manager, boolean isOnTheFly) {
    return checkReferencesIn(field, manager);
  }

  private static ProblemDescriptor[] merge(ProblemDescriptor[] a, ProblemDescriptor[] b) {
    if (a == null || a.length == 0) return b;
    if (b == null || b.length == 0) return a;
    ProblemDescriptor[] res = new ProblemDescriptor[a.length + b.length];
    System.arraycopy(a, 0, res, 0, a.length);
    System.arraycopy(b, 0, res, a.length, b.length);
    return res;
  }

  @Nullable
  private ProblemDescriptor[] checkReferencesIn(@Nullable PsiElement elt, InspectionManager manager) {
    if (elt == null || !isInProject(elt, manager)) return null;
    final MyVisitor visitor = new MyVisitor();
    elt.accept(visitor);
    final List<PsiElement> results = visitor.getResults();
    if (results == null) return null;
    ProblemDescriptor[] descriptors = new ProblemDescriptor[results.size()];
    for (int i = 0; i < descriptors.length; i++) {
      descriptors[i] = manager .createProblemDescriptor(results.get(i), "Usage of the API documented as @since 1.5", (LocalQuickFix)null,
                                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

    }
    return descriptors;
  }

  private boolean isInProject(final PsiElement elt, final InspectionManager manager) {
    return PsiManager.getInstance(manager.getProject()).isInProject(elt);
  }

  private static class MyVisitor extends PsiRecursiveElementVisitor {
    private List<PsiElement> results = null;

    public void visitDocComment(PsiDocComment comment) {
      // No references inside doc comment are of interest.
    }

    public void visitClass(PsiClass aClass) {
      // Don't go into classes (anonymous, locals).
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      final PsiElement resolved = reference.resolve();

      if (resolved instanceof PsiCompiledElement && resolved instanceof PsiMember) {
        if (checkMember((PsiMember)resolved)) {
          registerError(reference);
        }
      }
    }

    private boolean checkMember(final PsiMember member) {
      if (member == null) return false;

      // Annotations caught by special inspection if necessary
      if (member instanceof PsiClass && ((PsiClass)member).isAnnotationType()) return false;

      if (member instanceof PsiAnonymousClass) return false;
      if (member.getContainingClass() instanceof PsiAnonymousClass) return false;
      if (member instanceof PsiClass && !(member.getParent() instanceof PsiClass || member.getParent() instanceof PsiFile)) return false;

      return ourForbiddenAPI.contains(getSignature(member)) || checkMember(member.getContainingClass());
    }

    private void registerError(PsiJavaCodeReferenceElement reference) {
      if (results == null) {
        results = new ArrayList<PsiElement>(1);
      }
      results.add(reference);
    }

    public List<PsiElement> getResults() {
      return results;
    }
  }

  private static String getSignature(PsiMember member) {
    if (member instanceof PsiClass) {
      //noinspection ConstantConditions
      return ((PsiClass)member).getQualifiedName();
    }
    if (member instanceof PsiField) {
      return getSignature(member.getContainingClass()) + "#" + member.getName();
    }
    if (member instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)member;
      StringBuffer buf = new StringBuffer();
      buf.append(getSignature(method.getContainingClass()));
      buf.append('#');
      buf.append(method.getName());
      buf.append('(');
      final PsiType[] params = method.getSignature(PsiSubstitutor.EMPTY).getParameterTypes();
      for (PsiType type : params) {
        buf.append(type.getCanonicalText());
        buf.append(";");
      }
      buf.append(')');
      return buf.toString();
    }
    assert false;
    return null;
  }

  public static void generateList(Project project) {
    final VirtualFile srcroot = VirtualFileManager.getInstance()
      .findFileByUrl(ProjectRootManager.getInstance(project).getProjectJdk().getRootProvider().getUrls(OrderRootType.SOURCES)[0]);

    PsiDirectory dir = PsiManager.getInstance(project).findDirectory(srcroot);

    assert dir != null;
    dir.accept(new PsiRecursiveElementVisitor() {
      public void visitFile(PsiFile file) {
        if (file instanceof PsiJavaFile) {
          final PsiJavaFile javaFile = (PsiJavaFile)file;
          final PsiClass[] classes = javaFile.getClasses();
          for (int i = 0; i < classes.length; i++) {
            generateMember(classes[i]);
          }
        }
      }

      @SuppressWarnings({"UseOfSystemOutOrSystemErr"})
      private void generateMember(final PsiDocCommentOwner member) {
        if (member == null) return;

        if (member instanceof PsiClass && "java.lang.Enum".equals(((PsiClass)member).getQualifiedName())) {
          return;
        }

        if (isMarked(member)) {
          if (member instanceof PsiMethod) {
            final PsiMethod method = (PsiMethod)member;
            final PsiMethod[] supers = method.findSuperMethods();
            for (PsiMethod spr : supers) {
              if (spr != method) {
                final PsiClass klass = spr.getContainingClass();
                if (klass != null && !klass.isInterface() && !isMarked(spr) && !isMarked(klass)) return;
              }
            }
          }
          System.out.println(getSignature(member));
        }

        if (member instanceof PsiClass) {
          final PsiClass psiClass = (PsiClass)member;
          for (PsiMethod method : psiClass.getMethods()) {
            generateMember(method);
          }
          for (PsiClass inner : psiClass.getInnerClasses()) {
            generateMember(inner);
          }
          for (PsiField field : psiClass.getFields()) {
            generateMember(field);
          }
        }
      }
    });
  }

  private static boolean isMarked(PsiDocCommentOwner member) {
    member = (PsiDocCommentOwner)member.getNavigationElement();
    final PsiDocComment comm = member.getDocComment();
    if (comm != null) {
      final PsiDocTag tag = comm.findTagByName("since");
      if (tag != null) {
        final PsiDocTagValue value = tag.getValueElement();
        if (value != null) {
          if (value.getText().trim().equals("1.5")) {
            return true;
          }
        }
      }
    }
    return false;
  }

}
