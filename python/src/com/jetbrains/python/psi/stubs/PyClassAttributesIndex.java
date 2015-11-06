package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyClassAttributesIndex extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String, PyClass> KEY = StubIndexKey.createIndexKey("Py.class.attributes");

  @NotNull
  @Override
  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }

  public static Collection<PyClass> find(@NotNull String name, @NotNull Project project) {
    return StubIndex.getElements(KEY, name, project, GlobalSearchScope.allScope(project), PyClass.class);
  }

  /**
   * Returns all attributes: methods, class and instance fields that are declared directly in the specified class
   * (not taking inheritance into account).
   * <p/>
   * This method <b>must not</b> access the AST because it is being called during stub indexing.
   */
  @NotNull
  public static List<String> getAllDeclaredAttributeNames(@NotNull PyClass pyClass) {
    final List<PsiNamedElement> members = ContainerUtil.<PsiNamedElement>concat(pyClass.getInstanceAttributes(),
                                                                                pyClass.getClassAttributes(),
                                                                                Arrays.asList(pyClass.getMethods()));

    return ContainerUtil.mapNotNull(members, new Function<PsiNamedElement, String>() {
      @Override
      public String fun(PsiNamedElement expression) {
        final String attrName = expression.getName();
        return attrName != null ? attrName : null;
      }
    });
  }
}
