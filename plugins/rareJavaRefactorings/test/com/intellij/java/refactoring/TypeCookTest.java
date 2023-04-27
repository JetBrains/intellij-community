// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.typeCook.Settings;
import com.intellij.refactoring.typeCook.deductive.builder.ReductionSystem;
import com.intellij.refactoring.typeCook.deductive.builder.SystemBuilder;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;
import com.intellij.refactoring.typeCook.deductive.resolver.ResolverTree;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class TypeCookTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("rareJavaRefactorings") + "/testData/typeCook/";
  }

  public void testT01() {
    start();
  }

  public void testT02() {
    start();
  }

  public void testT03() {
    start();
  }

  public void testT04() {
    start();
  }

  public void testT05() {
    start();
  }

  public void testT06() {
    start();
  }

  public void testT07() {
    start();
  }

  public void testT08() {
    start();
  }

  public void testT09() {
    start();
  }

  public void testT10() {
    start();
  }

  public void testT11() {
    start();
  }

  public void testT12() {
    start();
  }


  public void testT13() {
    start();
  }

  public void testT14() {
    start();
  }

  public void testT15() {
    start();
  }

  public void testT16() {
    start();
  }

  public void testT17() {
    start();
  }

  public void testT18() {
    start();
  }

  public void testT19() {
    start();
  }

  public void testT20() {
    start();
  }

  public void testT21() {
    start();
  }

  public void testT22() {
    start();
  }

  public void testT23() {
    start();
  }

  public void testT24() {
    start();
  }

  public void testT25() {
    start();
  }

  public void testT26() {
    start();
  }

  public void testT27() {
    start();
  }

  public void testT28() {
    start();
  }

  public void testT29() {
    start();
  }

  public void testT30() {
    start();
  }

  public void testT31() {
    start();
  }

  // Waits for correct NCA...
  //public void testT32() throws Exception {
  // start();
  //}

  public void testT33() {
    start();
  }

  public void testT34() {
    start();
  }

  public void testT35() {
    start();
  }

  public void testT36() {
    start();
  }

  public void testT37() {
    start();
  }

  public void testT38() {
    start();
  }

  public void testT39() {
    start();
  }

  public void testT40() {
    start();
  }

  public void testT41() {
    start();
  }

  public void testT42() {
    start();
  }

  public void testT43() {
    start();
  }

  public void testT44() {
    start();
  }

  public void testT45() {
    start();
  }

  public void testT46() {
    start();
  }

  public void testT47() {
    start();
  }

  public void testT48() {
    start();
  }

  public void testT49() {
    start();
  }

  public void testT50() {
    start();
  }

  public void testT51() {
    start();
  }

  public void testT52() {
    start();
  }

  public void testT53() {
    start();
  }

  public void testT54() {
    start();
  }

  public void testT55() {
    start();
  }

  public void testT56() {
    start();
  }

  public void testT57() {
    start();
  }

  public void testT58() {
    start();
  }

  public void testT59() {
    start();
  }

  public void testT60() {
    start();
  }

  public void testT61() {
    start();
  }

  public void testT62() {
    start();
  }

  public void testT63() {
    start();
  }

  public void testT64() {
    start();
  }

  public void testT65() {
    start();
  }

  public void testT66() {
    start();
  }

  public void testT67() {
    start();
  }

  public void testT68() {
    start();
  }

  public void testT69() {
    start();
  }

  public void testT70() {
    start();
  }

  public void testT71() {
    start();
  }

  public void testT72() {
    start();
  }

  public void testT73() {
    start();
  }

  public void testT74() {
    start();
  }

  public void testT75() {
    start();
  }

  public void testT76() {
    start();
  }

  public void testT77() {
    start();
  }

  public void testT78() {
    start();
  }

  public void testT79() {
    start();
  }

  public void testT80() {
    start();
  }

  // Too conservative.... Waiting for better times
  //public void testT81() throws Exception {
  //  start();
  //}

  public void testT82() {
    start();
  }

  public void testT83() {
    start();
  }

  public void testT84() {
    start();
  }

  public void testT85() {
    start();
  }

  public void testT86() {
    start();
  }

  public void testT87() {
    start();
  }

  public void testT88() {
    start();
  }

  public void testT89() {
    start();
  }

  public void testT90() {
    start();
  }

  public void testT91() {
    start();
  }

  public void testT92() {
    start();
  }

  public void testT93() {
    start();
  }

  public void testT94() {
    start();
  }

  public void testT95() {
    start();
  }

  public void testT96() {
    start();
  }

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

  public void testT100() {
    start();
  }

  public void testT101() {
    start();
  }

  public void testT102() {
    start();
  }

  public void testT103() {
    start();
  }

  public void testT104() {
    start();
  }

  public void testT105() {
    start();
  }

  public void testT106() {
    start();
  }

  public void testT107() {
    start();
  }

  public void testT108() {
    start();
  }

  public void testT109() {
    start();
  }

  public void testT110() {
    start();
  }

  public void testT111() {
    start();
  }

  public void testT112() {
    start();
  }

  public void testT113() {
    start();
  }

  public void testT114() {
    start();
  }

  public void testT115() {
    start();
  }

  public void testT116() {
    start();
  }

  public void testT117() {
    start();
  }

  public void testT118() {
    start();
  }

  public void testT119() {
    start();
  }

  public void testT120() {
    start();
  }

  public void testT121() {
    start();
  }

  //public void testT122() throws Exception {
  //  start();
  //}

  public void testT123() {
    start();
  }

  public void testT124() {
    start();
  }

  public void testT125() {
    start();
  }

  public void testT126() {
    start();
  }

  public void testT127() {
    start();
  }

  public void testT128() {
    start();
  }

  public void testT129() {
    start();
  }

  public void testT130() {
    start();
  }

  public void testT131() {
    start();
  }

  public void testT132() {
    start();
  }

  public void testT133() {
    start();
  }

  public void testT134() {
    start();
  }

  public void testT135() {
    start();
  }

  public void testT136() {
    start();
  }

  public void testT137() {
    start();
  }

  //public void testT138() throws Exception {
  //   start();
  // }

  public void testT139() {
    start();
  }

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

  public void testT146() {
        start();
  }

  public void testT147() {
        start();
  }

  public void testT148() {
        start();
  }

  public void testT149() {
        start(true);
  }

  public void testT150() {
        start();
  }

  public void testT151() {
        start();
  }

  public void testT152() {
        start();
  }

  public void testConvertToDiamond() {
    final LanguageLevelProjectExtension levelProjectExtension = LanguageLevelProjectExtension.getInstance(getProject());
    final LanguageLevel oldLevel = levelProjectExtension.getLanguageLevel();
    try {
      levelProjectExtension.setLanguageLevel(LanguageLevel.JDK_1_8);
      start();
    }
    finally {
      levelProjectExtension.setLanguageLevel(oldLevel);
    }
  }

  public void start() {
    start(false);
  }

  public void start(final boolean cookObjects) {
    doTest(() -> this.performAction(cookObjects));
  }

  private void performAction(final boolean cookObjects) throws Exception {
    PsiClass aClass = myFixture.findClass("Test");
    
    SystemBuilder b = new SystemBuilder(getProject(),
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

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_6;
  }
}
