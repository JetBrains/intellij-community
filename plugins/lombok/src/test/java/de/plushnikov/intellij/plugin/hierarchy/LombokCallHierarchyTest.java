package de.plushnikov.intellij.plugin.hierarchy;

import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.ide.hierarchy.call.CallerMethodsTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewTestFixture;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Tests for CallHierarchy with Lombok annotations.
 * Compares the call hierarchy of Lombok-generated methods with equivalent plain Java implementations.
 * The test normalizes class names in the hierarchy dumps so that only the caller methods matter.
 * Only caller methods are compared, excluding internal class hierarchy elements.
 */
public class LombokCallHierarchyTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/hierarchy";
  }

  // ==================== @Getter Comparison Test ====================

  public void testGetterHierarchyComparison() {
    doCompareFieldHierarchies(
      "GetterLombok", "GetterLombokCaller", "name",
      "GetterPlainJava", "GetterPlainJavaCaller"
    );
  }

  // ==================== @Setter Comparison Test ====================

  public void testSetterHierarchyComparison() {
    doCompareFieldHierarchies(
      "SetterLombok", "SetterLombokCaller", "value",
      "SetterPlainJava", "SetterPlainJavaCaller"
    );
  }

  // ==================== @Data Comparison Test ====================

  public void testDataHierarchyComparison() {
    doCompareFieldHierarchies(
      "DataLombok", "DataLombokCaller", "id",
      "DataPlainJava", "DataPlainJavaCaller"
    );
  }

  // ==================== @Value Comparison Test ====================

  public void testValueHierarchyComparison() {
    doCompareFieldHierarchies(
      "ValueLombok", "ValueLombokCaller", "code",
      "ValuePlainJava", "ValuePlainJavaCaller"
    );
  }

  // ==================== @ToString Comparison Test ====================

  public void testToStringHierarchyComparison() {
    doCompareFieldHierarchies(
      "ToStringLombok", "ToStringLombokCaller", "description",
      "ToStringPlainJava", "ToStringPlainJavaCaller"
    );
  }

  // ==================== @Builder Comparison Test ====================

  public void testBuilderHierarchyComparison() {
    doCompareFieldHierarchies(
      "BuilderLombok", "BuilderLombokCaller", "title",
      "BuilderPlainJava", "BuilderPlainJavaCaller"
    );
  }

  // ==================== Helper Methods ====================

  /**
   * Compares the call hierarchy of a field in a Lombok class with a one in a plain Java class.
   * The hierarchies are normalized to remove class name differences, so only the method structure matters.
   */
  private void doCompareFieldHierarchies(
    String lombokClassName, String lombokCallerClassName, String fieldName,
    String plainJavaClassName, String plainJavaCallerClassName) {

    // Load all files together
    myFixture.configureByFiles(
      "/" + lombokClassName.replace('$', '/') + ".java",
      "/" + lombokCallerClassName + ".java",
      "/" + plainJavaClassName.replace('$', '/').split("\\$")[0] + ".java",
      "/" + plainJavaCallerClassName + ".java"
    );

    // Get Lombok hierarchy (field-based)
    @NotNull CallerMethodsTreeStructure lombokHierarchy = getFieldCallerHierarchy(lombokClassName, fieldName);

    // Get Plain Java hierarchy (field-based)
    @NotNull CallerMethodsTreeStructure plainJavaHierarchy = getFieldCallerHierarchy(plainJavaClassName, fieldName);

    final Comparator<NodeDescriptor<?>> nodeDescriptorComparator = JavaHierarchyUtil.getComparator(getProject());
    String plainDumped = HierarchyViewTestFixture.dump(plainJavaHierarchy, null, nodeDescriptorComparator, 0);

    String plainDumpedNormalized = plainDumped.replaceAll(plainJavaClassName, lombokClassName);
    plainDumpedNormalized = plainDumpedNormalized.replaceAll(plainJavaCallerClassName, lombokCallerClassName);

    final String equalsMethodCall = lombokClassName + ".equals(Object)";
    if(StringUtil.getOccurrenceCount(plainDumpedNormalized, equalsMethodCall) > 1) {
      plainDumpedNormalized = removeFirstLineWithSubstring(plainDumpedNormalized, equalsMethodCall);
    }

    HierarchyViewTestFixture.doHierarchyTest(lombokHierarchy, plainDumpedNormalized);
  }

  public static String removeFirstLineWithSubstring(String text, String substring) {
    AtomicBoolean removed = new AtomicBoolean(false);

    return Arrays.stream(text.split("\\R"))
      .filter(line -> {
        if (!removed.get() && line.contains(substring)) {
          removed.set(true);
          return false; // erstes Vorkommen raus
        }
        return true;
      })
      .collect(Collectors.joining(System.lineSeparator()));
  }


  @NotNull
  private CallerMethodsTreeStructure getFieldCallerHierarchy(String className, String fieldName) {
    PsiClass psiClass = JavaPsiFacade.getInstance(getProject())
      .findClass(className, ProjectScope.getProjectScope(getProject()));
    assertNotNull("Class '" + className + "' not found", psiClass);

    PsiField field = psiClass.findFieldByName(fieldName, false);
    assertNotNull("Field '" + fieldName + "' not found in " + className, field);

    return new CallerMethodsTreeStructure(getProject(), field, HierarchyBrowserBaseEx.SCOPE_PROJECT);
  }
}
