package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import org.intellij.lang.annotations.Language;

public class LombokFlagUsageInspectionTest extends LombokInspectionTest {

  @Override
  protected InspectionProfileEntry getInspection() {
    return new LombokFlagUsageInspection();
  }

  /**
   * Test for Data annotation with flag usage set to WARNING
   */
  public void testDataFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.Data;

      <warning descr="Use of @Data is flagged according to Lombok configuration.">@Data</warning>
      public class DataFlagUsageWarningTest {
        private String field;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.data.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("DataFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Data annotation with flag usage set to ERROR
   */
  public void testDataFlagUsageError() {
    final @Language("JAVA") String testClassText = """
      import lombok.Data;

      <error descr="Use of @Data is flagged according to Lombok configuration.">@Data</error>
      public class DataFlagUsageErrorTest {
        private String field;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.data.flagUsage = ERROR
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("DataFlagUsageErrorTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Data annotation with flag usage set to ALLOW
   */
  public void testDataFlagUsageAllow() {
    final @Language("JAVA") String testClassText = """
      import lombok.Data;

      @Data
      public class DataFlagUsageAllowTest {
        private String field;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.data.flagUsage = ALLOW
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("DataFlagUsageAllowTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Value annotation with flag usage set to WARNING
   */
  public void testValueFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.Value;

      <warning descr="Use of @Value is flagged according to Lombok configuration.">@Value</warning>
      public class ValueFlagUsageWarningTest {
        private String field;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.value.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("ValueFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Getter annotation with flag usage set to WARNING
   */
  public void testGetterFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.Getter;

      public class GetterFlagUsageWarningTest {
        <warning descr="Use of @Getter is flagged according to Lombok configuration.">@Getter</warning>
        private String field;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.getter.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("GetterFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Setter annotation with flag usage set to WARNING
   */
  public void testSetterFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.Setter;

      public class SetterFlagUsageWarningTest {
        <warning descr="Use of @Setter is flagged according to Lombok configuration.">@Setter</warning>
        private String field;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.setter.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("SetterFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Builder annotation with flag usage set to WARNING
   */
  public void testBuilderFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.Builder;

      <warning descr="Use of @Builder is flagged according to Lombok configuration.">@Builder</warning>
      public class BuilderFlagUsageWarningTest {
        private String field;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.builder.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("BuilderFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for SuperBuilder annotation with flag usage set to WARNING
   */
  public void testSuperBuilderFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.experimental.SuperBuilder;

      <warning descr="Use of @SuperBuilder is flagged according to Lombok configuration.">@SuperBuilder</warning>
      public class SuperBuilderFlagUsageWarningTest {
        private String field;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.superBuilder.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("SuperBuilderFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Experimental flag usage set to WARNING (affects SuperBuilder)
   */
  public void testExperimentalFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.experimental.SuperBuilder;

      <warning descr="Use of @SuperBuilder is flagged according to Lombok configuration.">@SuperBuilder</warning>
      public class ExperimentalFlagUsageWarningTest {
        private String field;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.experimental.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("ExperimentalFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Getter(lazy=true) with lazy flag usage set to WARNING
   */
  public void testGetterLazyFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.Getter;

      public class GetterLazyFlagUsageWarningTest {
        <warning descr="Use of @Getter(lazy=true) is flagged according to Lombok configuration.">@Getter(lazy=true)</warning>
        private final String field = "test";
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.getter.lazy.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("GetterLazyFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for onX parameter with flag usage set to WARNING
   */
  public void testOnXFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.Getter;
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;

      public class OnXFlagUsageWarningTest {
        <warning descr="Use of @lombok.Getter(onMethod=...) is flagged according to Lombok configuration.">@Getter(onMethod_=@TestAnnotation)</warning>
        private String field;

        @Target(ElementType.METHOD)
        @Retention(RetentionPolicy.RUNTIME)
        public @interface TestAnnotation {}
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.onX.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("OnXFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for EqualsAndHashCode annotation with flag usage set to WARNING
   */
  public void testEqualsAndHashCodeFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.EqualsAndHashCode;

      <warning descr="Use of @EqualsAndHashCode is flagged according to Lombok configuration.">@EqualsAndHashCode</warning>
      public class EqualsAndHashCodeFlagUsageWarningTest {
        private String field;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.equalsAndHashCode.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("EqualsAndHashCodeFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for ToString annotation with flag usage set to WARNING
   */
  public void testToStringFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.ToString;

      <warning descr="Use of @ToString is flagged according to Lombok configuration.">@ToString</warning>
      public class ToStringFlagUsageWarningTest {
        private String field;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.toString.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("ToStringFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for With annotation with flag usage set to WARNING
   */
  public void testWithFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.With;

      public class WithFlagUsageWarningTest {
        <warning descr="Use of @With is flagged according to Lombok configuration.">@With</warning>
        private final String field = "test";
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.with.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("WithFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for SneakyThrows annotation with flag usage set to WARNING
   */
  public void testSneakyThrowsFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.SneakyThrows;

      public class SneakyThrowsFlagUsageWarningTest {
        <warning descr="Use of @SneakyThrows is flagged according to Lombok configuration.">@SneakyThrows</warning>
        public void method() {
          throw new Exception("Test");
        }
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.sneakyThrows.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("SneakyThrowsFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Synchronized annotation with flag usage set to WARNING
   */
  public void testSynchronizedFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.Synchronized;

      public class SynchronizedFlagUsageWarningTest {
        <warning descr="Use of @Synchronized is flagged according to Lombok configuration.">@Synchronized</warning>
        public void method() {
          System.out.println("Test");
        }
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.synchronized.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("SynchronizedFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Cleanup annotation with flag usage set to WARNING
   */
  public void testCleanupFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.Cleanup;
      import java.io.FileInputStream;
      import java.io.IOException;

      public class CleanupFlagUsageWarningTest {
        public void method() throws IOException {
          <warning descr="Use of @Cleanup is flagged according to Lombok configuration.">@Cleanup</warning>
          FileInputStream stream = new FileInputStream("test.txt");
        }
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.cleanup.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("CleanupFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for NonNull annotation with flag usage set to WARNING
   */
  public void testNonNullFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.NonNull;

      public class NonNullFlagUsageWarningTest {
        <warning descr="Use of @NonNull is flagged according to Lombok configuration.">@NonNull</warning>
        private String field;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.nonNull.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("NonNullFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Delegate annotation with flag usage set to WARNING
   */
  public void testDelegateFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.Delegate;
      import java.util.ArrayList;
      import java.util.Collection;

      public class DelegateFlagUsageWarningTest {
        <warning descr="Use of @Delegate is flagged according to Lombok configuration.">@Delegate</warning>
        private final Collection<String> collection = new ArrayList<>();
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.delegate.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("DelegateFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Jacksonized annotation with flag usage set to WARNING
   */
  public void testJacksonizedFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.Builder;
      import lombok.extern.jackson.Jacksonized;

      <warning descr="Use of @Jacksonized is flagged according to Lombok configuration.">@Jacksonized</warning>
      @Builder
      public class JacksonizedFlagUsageWarningTest {
        private String field;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.jacksonized.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("JacksonizedFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for AllArgsConstructor annotation with flag usage set to WARNING
   */
  public void testAllArgsConstructorFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.AllArgsConstructor;

      <warning descr="Use of @AllArgsConstructor is flagged according to Lombok configuration.">@AllArgsConstructor</warning>
      public class AllArgsConstructorFlagUsageWarningTest {
        private String field1;
        private int field2;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.allArgsConstructor.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("AllArgsConstructorFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for NoArgsConstructor annotation with flag usage set to WARNING
   */
  public void testNoArgsConstructorFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.NoArgsConstructor;

      <warning descr="Use of @NoArgsConstructor is flagged according to Lombok configuration.">@NoArgsConstructor</warning>
      public class NoArgsConstructorFlagUsageWarningTest {
        private String field1;
        private int field2;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.noArgsConstructor.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("NoArgsConstructorFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for RequiredArgsConstructor annotation with flag usage set to WARNING
   */
  public void testRequiredArgsConstructorFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.RequiredArgsConstructor;

      <warning descr="Use of @RequiredArgsConstructor is flagged according to Lombok configuration.">@RequiredArgsConstructor</warning>
      public class RequiredArgsConstructorFlagUsageWarningTest {
        private final String field1;
        private int field2;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.requiredArgsConstructor.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("RequiredArgsConstructorFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for AnyConstructor flag usage set to WARNING (affects all constructor annotations)
   */
  public void testAnyConstructorFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.AllArgsConstructor;

      <warning descr="Use of @AllArgsConstructor is flagged according to Lombok configuration.">@AllArgsConstructor</warning>
      public class AnyConstructorFlagUsageWarningTest {
        private String field1;
        private int field2;
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.anyConstructor.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("AnyConstructorFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Helper annotation with flag usage set to WARNING
   */
  public void testHelperFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.experimental.Helper;

      <warning descr="Use of @Helper is flagged according to Lombok configuration.">@Helper</warning>
      public class HelperFlagUsageWarningTest {
        public void helperMethod() {
          System.out.println("Helper method");
        }
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.helper.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("HelperFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for UtilityClass annotation with flag usage set to WARNING
   */
  public void testUtilityClassFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.experimental.UtilityClass;

      <warning descr="Use of @UtilityClass is flagged according to Lombok configuration.">@UtilityClass</warning>
      public class UtilityClassFlagUsageWarningTest {
        public static void utilityMethod() {
          System.out.println("Utility method");
        }
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.utilityClass.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("UtilityClassFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Tolerate annotation with flag usage set to WARNING
   */
  public void testTolerateFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.experimental.Tolerate;
      import lombok.Getter;

      public class TolerateFlagUsageWarningTest {
        @Getter
        private String name;

        <warning descr="Use of @Tolerate is flagged according to Lombok configuration.">@Tolerate</warning>
        public void setName(String name, String suffix) {
          this.name = name + suffix;
        }
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.experimental.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("TolerateFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for Locked annotation with flag usage set to WARNING
   */
  public void testLockedFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.Locked;

      public class LockedFlagUsageWarningTest {
        <warning descr="Use of @Locked is flagged according to Lombok configuration.">@Locked</warning>
        public void lockedMethod() {
          System.out.println("Locked method");
        }
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.locked.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("LockedFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }
  /**
   * Test for val on local variables with flag usage set to WARNING
   */
  public void testValLocalVariableFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.val;

      public class ValLocalVariableFlagUsageWarningTest {
        public void method() {
          <warning descr="Use of @lombok.val is flagged according to Lombok configuration.">val</warning> x = "test";
        }
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.val.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("ValLocalVariableFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }

  /**
   * Test for val on parameters with flag usage set to WARNING
   */
  public void testValParameterFlagUsageWarning() {
    final @Language("JAVA") String testClassText = """
      import lombok.val;
      import java.util.Arrays;
      import java.util.List;

      public class ValParameterFlagUsageWarningTest {
        public void method() {
          List<String> items = Arrays.asList("a", "b", "c");
          for (<warning descr="Use of @lombok.val is flagged according to Lombok configuration.">val</warning> item : items) {
            System.out.println(item);
          }
        }
      }""";

    final @Language("PROPERTIES") String lombokConfigText = """
      lombok.val.flagUsage = WARNING
      """;

    myFixture.configureByText("lombok.config", lombokConfigText);
    myFixture.configureByText("ValParameterFlagUsageWarningTest.java", testClassText);
    myFixture.checkHighlighting();
  }
}
