// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.platform.bazel.runfiles.BazelLabel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.refactoring.typeCook.deductive.builder.ReductionSystem;
import com.intellij.refactoring.typeCook.deductive.builder.SystemBuilder;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;
import com.intellij.refactoring.typeCook.deductive.resolver.ResolverTree;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.common.BazelTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class TypeCookTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_6);
  private final TestFixture<LightMultiFileFixture> multiFile = lightMultiFileFixture(projectFixture, testDataPath());

  private static String testDataPath() {
    if (BazelTestUtil.isUnderBazelTest()) {
      var label = BazelLabel.Companion.fromString("@community//plugins/rareJavaRefactorings:testData");
      return BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath() + "/typeCook/";
    }
    return PluginPathManager.getPluginHomePath("rareJavaRefactorings") + "/testData/typeCook/";
  }

  @Test
  public void testT01() {
    start();
  }

  @Test
  public void testT02() {
    start();
  }

  @Test
  public void testT03() {
    start();
  }

  @Test
  public void testT04() {
    start();
  }

  @Test
  public void testT05() {
    start();
  }

  @Test
  public void testT06() {
    start();
  }

  @Test
  public void testT07() {
    start();
  }

  @Test
  public void testT08() {
    start();
  }

  @Test
  public void testT09() {
    start();
  }

  @Test
  public void testT10() {
    start();
  }

  @Test
  public void testT11() {
    start();
  }

  @Test
  public void testT12() {
    start();
  }

  @Test
  public void testT13() {
    start();
  }

  @Test
  public void testT14() {
    start();
  }

  @Test
  public void testT15() {
    start();
  }

  @Test
  public void testT16() {
    start();
  }

  @Test
  public void testT17() {
    start();
  }

  @Test
  public void testT18() {
    start();
  }

  @Test
  public void testT19() {
    start();
  }

  @Test
  public void testT20() {
    start();
  }

  @Test
  public void testT21() {
    start();
  }

  @Test
  public void testT22() {
    start();
  }

  @Test
  public void testT23() {
    start();
  }

  @Test
  public void testT24() {
    start();
  }

  @Test
  public void testT25() {
    start();
  }

  @Test
  public void testT26() {
    start();
  }

  @Test
  public void testT27() {
    start();
  }

  @Test
  public void testT28() {
    start();
  }

  @Test
  public void testT29() {
    start();
  }

  @Test
  public void testT30() {
    start();
  }

  @Test
  public void testT31() {
    start();
  }

  // Waits for correct NCA...
  //public void testT32() throws Exception {
  // start();
  //}

  @Test
  public void testT33() {
    start();
  }

  @Test
  public void testT34() {
    start();
  }

  @Test
  public void testT35() {
    start();
  }

  @Test
  public void testT36() {
    start();
  }

  @Test
  public void testT37() {
    start();
  }

  @Test
  public void testT38() {
    start();
  }

  @Test
  public void testT39() {
    start();
  }

  @Test
  public void testT40() {
    start();
  }

  @Test
  public void testT41() {
    start();
  }

  @Test
  public void testT42() {
    start();
  }

  @Test
  public void testT43() {
    start();
  }

  @Test
  public void testT44() {
    start();
  }

  @Test
  public void testT45() {
    start();
  }

  @Test
  public void testT46() {
    start();
  }

  @Test
  public void testT47() {
    start();
  }

  @Test
  public void testT48() {
    start();
  }

  @Test
  public void testT49() {
    start();
  }

  @Test
  public void testT50() {
    start();
  }

  @Test
  public void testT51() {
    start();
  }

  @Test
  public void testT52() {
    start();
  }

  @Test
  public void testT53() {
    start();
  }

  @Test
  public void testT54() {
    start();
  }

  @Test
  public void testT55() {
    start();
  }

  @Test
  public void testT56() {
    start();
  }

  @Test
  public void testT57() {
    start();
  }

  @Test
  public void testT58() {
    start();
  }

  @Test
  public void testT59() {
    start();
  }

  @Test
  public void testT60() {
    start();
  }

  @Test
  public void testT61() {
    start();
  }

  @Test
  public void testT62() {
    start();
  }

  @Test
  public void testT63() {
    start();
  }

  @Test
  public void testT64() {
    start();
  }

  @Test
  public void testT65() {
    start();
  }

  @Test
  public void testT66() {
    start();
  }

  @Test
  public void testT67() {
    start();
  }

  @Test
  public void testT68() {
    start();
  }

  @Test
  public void testT69() {
    start();
  }

  @Test
  public void testT70() {
    start();
  }

  @Test
  public void testT71() {
    start();
  }

  @Test
  public void testT72() {
    start();
  }

  @Test
  public void testT73() {
    start();
  }

  @Test
  public void testT74() {
    start();
  }

  @Test
  public void testT75() {
    start();
  }

  @Test
  public void testT76() {
    start();
  }

  @Test
  public void testT77() {
    start();
  }

  @Test
  public void testT78() {
    start();
  }

  @Test
  public void testT79() {
    start();
  }

  @Test
  public void testT80() {
    start();
  }

  // Too conservative.... Waiting for better times
  //public void testT81() throws Exception {
  //  start();
  //}

  @Test
  public void testT82() {
    start();
  }

  @Test
  public void testT83() {
    start();
  }

  @Test
  public void testT84() {
    start();
  }

  @Test
  public void testT85() {
    start();
  }

  @Test
  public void testT86() {
    start();
  }

  @Test
  public void testT87() {
    start();
  }

  @Test
  public void testT88() {
    start();
  }

  @Test
  public void testT89() {
    start();
  }

  @Test
  public void testT90() {
    start();
  }

  @Test
  public void testT91() {
    start();
  }

  @Test
  public void testT92() {
    start();
  }

  @Test
  public void testT93() {
    start();
  }

  @Test
  public void testT94() {
    start();
  }

  @Test
  public void testT95() {
    start();
  }

  @Test
  public void testT96() {
    start();
  }

  @Test
  public void testT97() {
    start();
  }

  // Wrong: inner class
  //public void testT98() throws Exception {
  //  start();
  //}

  // Wrong: anonymous
  //public void testT99() throws Exception {
  //  start();
  //}

  @Test
  public void testT100() {
    start();
  }

  @Test
  public void testT101() {
    start();
  }

  @Test
  public void testT102() {
    start();
  }

  @Test
  public void testT103() {
    start();
  }

  @Test
  public void testT104() {
    start();
  }

  @Test
  public void testT105() {
    start();
  }

  @Test
  public void testT106() {
    start();
  }

  @Test
  public void testT107() {
    start();
  }

  @Test
  public void testT108() {
    start();
  }

  @Test
  public void testT109() {
    start();
  }

  @Test
  public void testT110() {
    start();
  }

  @Test
  public void testT111() {
    start();
  }

  @Test
  public void testT112() {
    start();
  }

  @Test
  public void testT113() {
    start();
  }

  @Test
  public void testT114() {
    start();
  }

  @Test
  public void testT115() {
    start();
  }

  @Test
  public void testT116() {
    start();
  }

  @Test
  public void testT117() {
    start();
  }

  @Test
  public void testT118() {
    start();
  }

  @Test
  public void testT119() {
    start();
  }

  @Test
  public void testT120() {
    start();
  }

  @Test
  public void testT121() {
    start();
  }

  //public void testT122() throws Exception {
  //  start();
  //}

  @Test
  public void testT123() {
    start();
  }

  @Test
  public void testT124() {
    start();
  }

  @Test
  public void testT125() {
    start();
  }

  @Test
  public void testT126() {
    start();
  }

  @Test
  public void testT127() {
    start();
  }

  @Test
  public void testT128() {
    start();
  }

  @Test
  public void testT129() {
    start();
  }

  @Test
  public void testT130() {
    start();
  }

  @Test
  public void testT131() {
    start();
  }

  @Test
  public void testT132() {
    start();
  }

  @Test
  public void testT133() {
    start();
  }

  @Test
  public void testT134() {
    start();
  }

  @Test
  public void testT135() {
    start();
  }

  @Test
  public void testT136() {
    start();
  }

  @Test
  public void testT137() {
    start();
  }

  //public void testT138() throws Exception {
  //   start();
  // }

  @Test
  public void testT139() {
    start();
  }

  @Test
  public void testT140() {
    start();
  }

  //public void testT141() throws Exception {
  //  start();
  //}
  //
  //public void testT142() throws Exception {
  //  start();
  //}
  //
  //public void testT143() throws Exception {
  //    start();
  //}
  //
  //public void testT144() throws Exception {
  //      start();
  //}
  //
  //public void testT145() throws Exception {
  //      start();
  //}

  @Test
  public void testT146() {
        start();
  }

  @Test
  public void testT147() {
        start();
  }

  @Test
  public void testT148() {
        start();
  }

  @Test
  public void testT149() {
        start(true);
  }

  @Test
  public void testT150() {
        start();
  }

  @Test
  public void testT151() {
        start();
  }

  @Test
  public void testT152() {
        start();
  }

  @Test
  public void testConvertToDiamond() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    final LanguageLevel oldLevel = IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), LanguageLevel.JDK_1_8);
    try {
      start();
    }
    finally {
      IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), oldLevel);
    }
  }

  public void start() {
    start(false);
  }

  public void start(final boolean cookObjects) {
    multiFile.get().doTest(() -> this.performAction(cookObjects));
  }

  private void performAction(final boolean cookObjects) throws Exception {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    PsiClass aClass = myFixture.findClass("Test");
    
    SystemBuilder b = new SystemBuilder(myFixture.getProject(),
                                        new Settings() {
                                          @Override
                                          public boolean dropObsoleteCasts() {
                                            return true;
                                          }

                                          @Override
                                          public boolean preserveRawArrays() {
                                            return false;
                                          }

                                          @Override
                                          public boolean leaveObjectParameterizedTypesRaw() {
                                            return false;
                                          }

                                          @Override
                                          public boolean exhaustive() {
                                            return false;
                                          }

                                          @Override
                                          public boolean cookObjects(){
                                            return cookObjects;
                                          }

                                          @Override
                                          public boolean cookToWildcards(){
                                            return false;
                                          }
                                        });

    final ReductionSystem commonSystem = b.build(aClass);

    //System.out.println("System built:\n" + commonSystem);

    final ReductionSystem[] systems = commonSystem.isolate();

    //System.out.println("Systems isolated:\n" + commonSystem);

    ReductionSystem system = null;

    for (ReductionSystem s : systems) {
      if (s != null && system == null) {
        //System.out.println(s);
        system = s;
      }
    }

    Binding binding = null;

    if (system != null) {
      final ResolverTree tree = new ResolverTree(system);

      tree.resolve();

      binding = tree.getBestSolution();
    }

    String itemRepr = system != null ? system.dumpString() : commonSystem.dumpString();// d.resultString();
    myFixture.getTempDirFixture().createFile("Test.items", itemRepr);
    itemRepr = system != null ? system.dumpResult(binding) : commonSystem.dumpString(); //d.resultString();
    myFixture.getTempDirFixture().createFile("Test.1.items", itemRepr);
  }
}
