package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;

@TestApplication
public class StaticMethodImportLombokInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

  @Language("JAVA") static final String usingClassText = """
      package otherpackage;

      import somepackage.Place;
      import somepackage.SomeClass;
      import static somepackage.Place.builder<caret>;
      import static <error descr="Static imports of Lombok-generated methods doesn't work with javac">somepackage.SomeClass.create</error>;
      import static <error descr="Static imports of Lombok-generated methods doesn't work with javac">somepackage.SomeClass.getSomeInt</error>;
      import static java.lang.Math.max;
      import static java.util.Arrays.asList;


      public class OtherClass {
          public static void main(String[] args) {
              final Place myPlace = new Place("something", 123);
              System.out.println(myPlace);

              final Place builderPlace = builder().someStr("something").someInt(2345).build();
              System.out.println(builderPlace);

              final SomeClass someClass = create("something");
              System.out.println(someClass);

              SomeClass.setSomeInt(1);
              System.out.println(getSomeInt());

              System.out.println(asList("a", "b", "c"));

              System.out.println(Math.abs(1));
              System.out.println(max(1, 2));
          }
      }
      """;
  @Language("JAVA") static final String placeClassText = """
    package somepackage;

    import lombok.AllArgsConstructor;
    import lombok.Builder;

    @AllArgsConstructor
    @Builder
    public class Place {
      private String someStr;
      private int someInt;
    }
    """;

  @Language("JAVA") static final String someClassText = """
    package somepackage;

    import lombok.Data;
    import lombok.Getter;
    import lombok.RequiredArgsConstructor;
    import lombok.Setter;

    @Data
    @RequiredArgsConstructor(staticName = "create")
    public class SomeClass {
        private final String str;
        @Getter
        @Setter
        private static int someInt;
    }
    """;

  @Test
  public void testStaticImportOfLombokGeneratedMethods() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(StaticMethodImportLombokInspection.class);

    myFixture.addFileToProject("somepackage/Place.java", placeClassText);
    myFixture.addFileToProject("somepackage/SomeClass.java", someClassText);
    myFixture.configureByText("OtherClass.java", usingClassText);

    IntentionAction action = myFixture.findSingleIntention("Expand static import");
    myFixture.launchAction(action);
    myFixture.checkResult("""
      package otherpackage;

      import somepackage.Place;
      import somepackage.SomeClass;
      import static <error descr="Static imports of Lombok-generated methods doesn't work with javac">somepackage.SomeClass.create</error>;
      import static <error descr="Static imports of Lombok-generated methods doesn't work with javac">somepackage.SomeClass.getSomeInt</error>;
      import static java.lang.Math.max;
      import static java.util.Arrays.asList;


      public class OtherClass {
          public static void main(String[] args) {
              final Place myPlace = new Place("something", 123);
              System.out.println(myPlace);

              final Place builderPlace = Place.builder().someStr("something").someInt(2345).build();
              System.out.println(builderPlace);

              final SomeClass someClass = create("something");
              System.out.println(someClass);

              SomeClass.setSomeInt(1);
              System.out.println(getSomeInt());

              System.out.println(asList("a", "b", "c"));

              System.out.println(Math.abs(1));
              System.out.println(max(1, 2));
          }
      }
      """);
    myFixture.checkHighlighting();
  }

}
