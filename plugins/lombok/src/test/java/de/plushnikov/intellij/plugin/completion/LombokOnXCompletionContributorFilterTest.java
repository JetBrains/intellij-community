package de.plushnikov.intellij.plugin.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import org.intellij.lang.annotations.Language;

import java.util.Arrays;
import java.util.List;

public class LombokOnXCompletionContributorFilterTest extends AbstractLombokLightCodeInsightTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_8);
  }

  public void testOnConstructorJdk7() {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    testOnAnnotation("""
                           import lombok.AllArgsConstructor;

                           @AllArgsConstructor(<caret>)
                           public class OnXExample {
                               private final long unid;
                           }
                       """, Arrays.asList("access", "onConstructor", "onConstructor_", "staticName"));
  }

  public void testOnConstructor() {
    testOnAnnotation("""
                           import lombok.RequiredArgsConstructor;

                           @RequiredArgsConstructor(<caret>onConstructor_ = @Deprecated)
                           public class OnXExample {
                               private final long unid;
                           }
                       """, Arrays.asList("access", "onConstructor_", "staticName"));
  }

  public void testOnMethod() {
    testOnAnnotation("""
                           import lombok.Getter;

                           public class OnXExample {
                               @Getter(<caret>onMethod_ = {@Deprecated, @SuppressWarnings(value = "someId")})
                               private long unid;
                           }
                       """, Arrays.asList("lazy = true", "onMethod_", "value", "lazy = false"));
  }

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
    myFixture.configureByText(JavaFileType.INSTANCE, textInput);
    myFixture.complete(CompletionType.BASIC);
    List<String> strings = myFixture.getLookupElementStrings();
    assertSameElements(strings, expected);
  }
}
