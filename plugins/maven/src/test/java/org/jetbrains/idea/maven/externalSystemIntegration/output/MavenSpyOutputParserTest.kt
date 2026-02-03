// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output

import kotlinx.coroutines.runBlocking

class MavenSpyOutputParserTest : MavenBuildToolLogTestUtils() {

  fun testSuccessfullBuildWithTwoSubmodules() = runBlocking {
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

  fun testArchetypeRun() = runBlocking {
    failOnWarns {
      testCase(*fromFile("org/jetbrains/maven/buildlogs/test-scala-archetype.log"))
        .withSkippedOutput()
        .runAndFormatToString()
    }
  }

  fun testdependencyInSinleMojoFailed() = runBlocking {
    failOnWarns {
      assertSameLines("io.testproject:web-test-example:jar:1.1\n" +
                      "  resources\n" +
                      "  compile\n" +
                      "  testResources\n" +
                      "  testCompile\n" +
                      "  test\n" +
                      "  jar\n" +
                      "  single\n" +
                      "   dependencies\n" +
                      "    error:Failure to find io.testproject:java-sdk:pom:1.0 in https://repo.maven.apache.org/maven2 was cached in the local repository, resolution will not be reattempted until the update interval of central has elapsed or updates are forced\n" +
                      "    error:Could not find artifact io.testproject:example-addon-proxy:pom:0.0.1-SNAPSHOT\n" +
                      "  install",

      testCase(*fromFile("org/jetbrains/maven/buildlogs/single-io.testproject.log"))
        .withSkippedOutput()
        .runAndFormatToString())
    }
  }

  fun testSuccessfullBuildWithOutputTwoSubmodules() = runBlocking {
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

  fun `test parse build log with -q failed`() = runBlocking {
    failOnWarns {
      assertSameLines("error:Maven Run\n" +
                      " org.example:demo-old-version:pom:1.0-SNAPSHOT\n" +
                      " org.example:child1:jar:1.0-SNAPSHOT\n" +
                      "  resources\n" +
                      "  compile",
        testCase(*fromFile("org/jetbrains/maven/buildlogs/build-quiet-failed.log"))
          .withSkippedOutput()
          .runAndFormatToString())
    }
  }

  fun `test parse build log with -q`() = runBlocking {
    failOnWarns {
      assertSameLines("org.example:demo-old-version:pom:1.0-SNAPSHOT\n" +
                      " org.example:child1:jar:1.0-SNAPSHOT\n" +
                      "  resources\n" +
                      "  compile\n" +
                      "  testResources\n" +
                      "  testCompile\n" +
                      "  test\n" +
                      "  jar",
        testCase(*fromFile("org/jetbrains/maven/buildlogs/build-quiet-success.log"))
          .withSkippedOutput()
          .runAndFormatToString())
    }
  }

  fun `test parse build log no goal failed`() = runBlocking {
    failOnWarns {
      assertSameLines("error:",
        testCase(*fromFile("org/jetbrains/maven/buildlogs/build-no-goal-failed.log"))
          .withSkippedOutput()
          .runAndFormatToString())
    }
  }

  fun `test parse build log no pom failed`() = runBlocking {
    failOnWarns {
      assertSameLines("error:",
        testCase(*fromFile("org/jetbrains/maven/buildlogs/build-no-pom-failed.log"))
          .withSkippedOutput()
          .runAndFormatToString())
    }
  }
}
