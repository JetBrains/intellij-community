package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.usageView.UsageInfo;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import org.intellij.lang.annotations.Language;

import java.util.Collection;
import java.util.Map;

public class LombokReadWriteAccessFindUsagesTest extends AbstractLombokLightCodeInsightTestCase {

  @Language("JAVA")
  private static final String ACCESS_CLASS = """
            import java.util.Collections;
            public final class Access {
                public static void main(String[] args) {
                    final Pojo pojo = Pojo.builder()
                            .list(Collections.emptyList())
                            .string("helloString")
                            .build();
                    System.out.println(pojo.getList());
                    System.out.println(pojo.getString());
            }
    }""";
  @Language("JAVA")
  private static final String POJO_CLASS = """
    import lombok.Builder;
    import lombok.Data;

    import java.util.List;

    @Data
    @Builder
    public class Pojo {

        private List<String> list;

        private String string<caret>;

        public static void main(String[] args) {
            final Pojo pojo = new Pojo(List.of(), "");
            System.out.println(pojo);
            pojo.setString("someString");
            System.out.println(pojo.getString());
        }
    }""";

  public void testFindUsages() {
    PsiJavaFile pojoFile = (PsiJavaFile)myFixture.configureByText("Pojo.java", POJO_CLASS);
    PsiJavaFile accessFile = (PsiJavaFile)myFixture.configureByText("Access.java", ACCESS_CLASS);

    ReadWriteAccessDetector detector = ReadWriteAccessDetector.findDetector(pojoFile.getClasses()[0].getFields()[0]);
    assertInstanceOf(detector, LombokReadWriteAccessDetector.class);

    Map<String, ReadWriteAccessDetector.Access> expectedAccess = Map.of("getString", ReadWriteAccessDetector.Access.Read,
                                                                        "string", ReadWriteAccessDetector.Access.Write,
                                                                        "setString", ReadWriteAccessDetector.Access.Write);

    Collection<UsageInfo> usageInfos = myFixture.testFindUsages("Pojo.java", "Access.java");
    assertSize(4, usageInfos);
    for (UsageInfo usageInfo : usageInfos) {
      final PsiReferenceExpression usageInfoElement = (PsiReferenceExpression)usageInfo.getElement();

      final PsiMethod resolvedMethod = (PsiMethod)usageInfoElement.resolve();
      assertNotNull(resolvedMethod);

      final ReadWriteAccessDetector.Access expressionAccess = detector.getExpressionAccess(usageInfoElement);
      assertEquals(expectedAccess.get(resolvedMethod.getName()), expressionAccess);
    }
  }
}