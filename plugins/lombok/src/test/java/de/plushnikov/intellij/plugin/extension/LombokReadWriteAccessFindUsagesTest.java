package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class LombokReadWriteAccessFindUsagesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

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

  @Test
  public void testFindUsages() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFile pojoFile = (PsiJavaFile)myFixture.configureByText("Pojo.java", POJO_CLASS);
    PsiJavaFile accessFile = (PsiJavaFile)myFixture.configureByText("Access.java", ACCESS_CLASS);

    ReadWriteAccessDetector detector = ReadAction.computeBlocking(() -> ReadWriteAccessDetector.findDetector(pojoFile.getClasses()[0].getFields()[0]));
    assertInstanceOf(LombokReadWriteAccessDetector.class, detector);

    Map<String, ReadWriteAccessDetector.Access> expectedAccess = Map.of("getString", ReadWriteAccessDetector.Access.Read,
                                                                        "string", ReadWriteAccessDetector.Access.Write,
                                                                        "setString", ReadWriteAccessDetector.Access.Write);

    Collection<UsageInfo> usageInfos = myFixture.testFindUsages("Pojo.java", "Access.java");
    assertThat(usageInfos).hasSize(4);
    ReadAction.runBlocking(() -> {
      for (UsageInfo usageInfo : usageInfos) {
        final PsiReferenceExpression usageInfoElement = (PsiReferenceExpression)usageInfo.getElement();

        final PsiMethod resolvedMethod = (PsiMethod)usageInfoElement.resolve();
        assertNotNull(resolvedMethod);

        final ReadWriteAccessDetector.Access expressionAccess = detector.getExpressionAccess(usageInfoElement);
        assertEquals(expectedAccess.get(resolvedMethod.getName()), expressionAccess);
      }
    });
  }
}