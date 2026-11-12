package de.plushnikov.intellij.plugin.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class LombokOnXCompletionContributorFilterTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), LanguageLevel.JDK_1_8);
  }

  @Test
  public void testOnConstructorJdk7() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), LanguageLevel.JDK_1_7);
    testOnAnnotation("""
                           import lombok.AllArgsConstructor;

                           @AllArgsConstructor(<caret>)
                           public class OnXExample {
                               private final long unid;
                           }
                       """, Arrays.asList("access", "onConstructor", "onConstructor_", "staticName"));
  }

  @Test
  public void testOnConstructor() {
    testOnAnnotation("""
                           import lombok.RequiredArgsConstructor;

                           @RequiredArgsConstructor(<caret>onConstructor_ = @Deprecated)
                           public class OnXExample {
                               private final long unid;
                           }
                       """, Arrays.asList("access", "onConstructor_", "staticName"));
  }

  @Test
  public void testOnMethod() {
    testOnAnnotation("""
                           import lombok.Getter;

                           public class OnXExample {
                               @Getter(<caret>onMethod_ = {@Deprecated, @SuppressWarnings(value = "someId")})
                               private long unid;
                           }
                       """, Arrays.asList("lazy = true", "onMethod_", "value", "lazy = false"));
  }

  @Test
  public void testOnParam() {
    testOnAnnotation("""
                           import lombok.Setter;

                           public class OnXExample {
                               @Setter(<caret>onMethod_ = @Deprecated, onParam_ = @SuppressWarnings(value ="someOtherId"))
                               private long unid;
                           }
                       """, Arrays.asList("onMethod_", "onParam_", "value"));
  }

  private void testOnAnnotation(@Language("JAVA") String textInput, List<String> expected) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, textInput);
    myFixture.complete(CompletionType.BASIC);
    List<String> strings = myFixture.getLookupElementStrings();
    assertThat(strings).hasSameSizeAs(expected).hasSameElementsAs(expected);
  }
}
