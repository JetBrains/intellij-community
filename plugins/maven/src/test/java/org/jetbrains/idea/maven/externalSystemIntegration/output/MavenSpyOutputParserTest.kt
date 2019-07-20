// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output

import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenBuildToolLogTestUtils.failOnWarns

class MavenSpyOutputParserTest : MavenBuildToolLogTestUtils() {

  fun testSuccessfullBuildWithTwoSubmodules() {
    failOnWarns {
      assertSameLines("" +
                      " test:project:pom:1\n" +
                      "  install\n" +
                      " test:m1:jar:1\n" +
                      "  resources\n" +
                      "  compile\n" +
                      "  testResources\n" +
                      "  testCompile\n" +
                      "  test\n" +
                      "  jar\n" +
                      "  install\n" +
                      " test:m2:jar:1\n" +
                      "  resources\n" +
                      "  compile\n" +
                      "  testResources\n" +
                      "  testCompile\n" +
                      "  test\n" +
                      "  jar\n" +
                      "  install",
                      testCase(*fromFile("org/jetbrains/maven/buildlogs/Project2Modules.log"))
                        .withSkippedOutput()
                        .runAndFormatToString())
    }
  }

  fun testArchetypeRun() {
    failOnWarns {
      testCase(*fromFile("org/jetbrains/maven/buildlogs/test-scala-archetype.log"))
        .withSkippedOutput()
        .runAndFormatToString()
    }
  }

  fun testSuccessfullBuildWithOutputTwoSubmodules() {
    failOnWarns {
      assertSameLines("test:project:pom:1\n" +
                      "  [INFO]\n" +
                      "  [INFO] ------------------------------------------------------------------------\n" +
                      "  [INFO] Building project 1\n" +
                      "  [INFO] ------------------------------------------------------------------------\n" +
                      "  install\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-install-plugin:2.4:install (default-install) @ project ---\n" +
                      "   [INFO] --- maven-install-plugin:2.4:install (default-install) @ project ---\n" +
                      "  [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\pom.xml to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\project\\1\\project-1.pom\n" +
                      "   [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\pom.xml to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\project\\1\\project-1.pom\n" +
                      " test:m1:jar:1\n" +
                      "  [INFO]\n" +
                      "  [INFO] ------------------------------------------------------------------------\n" +
                      "  [INFO] Building m1 1\n" +
                      "  [INFO] ------------------------------------------------------------------------\n" +
                      "  resources\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ m1 ---\n" +
                      "   [INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ m1 ---\n" +
                      "  [WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources, i.e. build is platform dependent!\n" +
                      "   [WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources, i.e. build is platform dependent!\n" +
                      "  [INFO] skip non existing resourceDirectory C:\\dev\\idea\\testprojects\\maven-2-err\\m1\\src\\main\\resources\n" +
                      "   [INFO] skip non existing resourceDirectory C:\\dev\\idea\\testprojects\\maven-2-err\\m1\\src\\main\\resources\n" +
                      "  compile\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-compiler-plugin:3.8.0:compile (default-compile) @ m1 ---\n" +
                      "   [INFO] --- maven-compiler-plugin:3.8.0:compile (default-compile) @ m1 ---\n" +
                      "  [INFO] Nothing to compile - all classes are up to date\n" +
                      "   [INFO] Nothing to compile - all classes are up to date\n" +
                      "  testResources\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-resources-plugin:2.6:testResources (default-testResources) @ m1 ---\n" +
                      "   [INFO] --- maven-resources-plugin:2.6:testResources (default-testResources) @ m1 ---\n" +
                      "  [WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources, i.e. build is platform dependent!\n" +
                      "   [WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources, i.e. build is platform dependent!\n" +
                      "  [INFO] skip non existing resourceDirectory C:\\dev\\idea\\testprojects\\maven-2-err\\m1\\src\\test\\resources\n" +
                      "   [INFO] skip non existing resourceDirectory C:\\dev\\idea\\testprojects\\maven-2-err\\m1\\src\\test\\resources\n" +
                      "  testCompile\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-compiler-plugin:3.8.0:testCompile (default-testCompile) @ m1 ---\n" +
                      "   [INFO] --- maven-compiler-plugin:3.8.0:testCompile (default-testCompile) @ m1 ---\n" +
                      "  [INFO] Nothing to compile - all classes are up to date\n" +
                      "   [INFO] Nothing to compile - all classes are up to date\n" +
                      "  test\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-surefire-plugin:2.12.4:test (default-test) @ m1 ---\n" +
                      "   [INFO] --- maven-surefire-plugin:2.12.4:test (default-test) @ m1 ---\n" +
                      "  jar\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-jar-plugin:2.4:jar (default-jar) @ m1 ---\n" +
                      "   [INFO] --- maven-jar-plugin:2.4:jar (default-jar) @ m1 ---\n" +
                      "  install\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-install-plugin:2.4:install (default-install) @ m1 ---\n" +
                      "   [INFO] --- maven-install-plugin:2.4:install (default-install) @ m1 ---\n" +
                      "  [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\m1\\target\\m1-1.jar to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\m1\\1\\m1-1.jar\n" +
                      "   [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\m1\\target\\m1-1.jar to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\m1\\1\\m1-1.jar\n" +
                      "  [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\m1\\pom.xml to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\m1\\1\\m1-1.pom\n" +
                      "   [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\m1\\pom.xml to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\m1\\1\\m1-1.pom\n" +
                      " test:m2:jar:1\n" +
                      "  [INFO]\n" +
                      "  [INFO] ------------------------------------------------------------------------\n" +
                      "  [INFO] Building m2 1\n" +
                      "  [INFO] ------------------------------------------------------------------------\n" +
                      "  resources\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ m2 ---\n" +
                      "   [INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ m2 ---\n" +
                      "  [WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources, i.e. build is platform dependent!\n" +
                      "   [WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources, i.e. build is platform dependent!\n" +
                      "  [INFO] skip non existing resourceDirectory C:\\dev\\idea\\testprojects\\maven-2-err\\m2\\src\\main\\resources\n" +
                      "   [INFO] skip non existing resourceDirectory C:\\dev\\idea\\testprojects\\maven-2-err\\m2\\src\\main\\resources\n" +
                      "  compile\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-compiler-plugin:3.8.0:compile (default-compile) @ m2 ---\n" +
                      "   [INFO] --- maven-compiler-plugin:3.8.0:compile (default-compile) @ m2 ---\n" +
                      "  [INFO] Nothing to compile - all classes are up to date\n" +
                      "   [INFO] Nothing to compile - all classes are up to date\n" +
                      "  testResources\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-resources-plugin:2.6:testResources (default-testResources) @ m2 ---\n" +
                      "   [INFO] --- maven-resources-plugin:2.6:testResources (default-testResources) @ m2 ---\n" +
                      "  [WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources, i.e. build is platform dependent!\n" +
                      "   [WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources, i.e. build is platform dependent!\n" +
                      "  [INFO] skip non existing resourceDirectory C:\\dev\\idea\\testprojects\\maven-2-err\\m2\\src\\test\\resources\n" +
                      "   [INFO] skip non existing resourceDirectory C:\\dev\\idea\\testprojects\\maven-2-err\\m2\\src\\test\\resources\n" +
                      "  testCompile\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-compiler-plugin:3.8.0:testCompile (default-testCompile) @ m2 ---\n" +
                      "   [INFO] --- maven-compiler-plugin:3.8.0:testCompile (default-testCompile) @ m2 ---\n" +
                      "  [INFO] Nothing to compile - all classes are up to date\n" +
                      "   [INFO] Nothing to compile - all classes are up to date\n" +
                      "  test\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-surefire-plugin:2.12.4:test (default-test) @ m2 ---\n" +
                      "   [INFO] --- maven-surefire-plugin:2.12.4:test (default-test) @ m2 ---\n" +
                      "  [INFO] No tests to run.\n" +
                      "   [INFO] No tests to run.\n" +
                      "  jar\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-jar-plugin:2.4:jar (default-jar) @ m2 ---\n" +
                      "   [INFO] --- maven-jar-plugin:2.4:jar (default-jar) @ m2 ---\n" +
                      "  install\n" +
                      "  [INFO]\n" +
                      "   [INFO]\n" +
                      "  [INFO] --- maven-install-plugin:2.4:install (default-install) @ m2 ---\n" +
                      "   [INFO] --- maven-install-plugin:2.4:install (default-install) @ m2 ---\n" +
                      "  [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\m2\\target\\m2-1.jar to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\m2\\1\\m2-1.jar\n" +
                      "   [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\m2\\target\\m2-1.jar to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\m2\\1\\m2-1.jar\n" +
                      "  [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\m2\\pom.xml to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\m2\\1\\m2-1.pom\n" +
                      "   [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\m2\\pom.xml to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\m2\\1\\m2-1.pom",
                      testCase(*fromFile("org/jetbrains/maven/buildlogs/Project2Modules.log"))
                        .runAndFormatToString())
    }
  }
}
