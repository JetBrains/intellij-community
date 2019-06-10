// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output

import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenBuildToolLogTestUtils.failOnWarns

class MavenSpyOutputParserTest : MavenBuildToolLogTestUtils() {

  fun testSuccessfullBuildWithTwoSubmodules() {
    failOnWarns {
      assertSameLines("" +
                      "Maven run\n" +
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
      assertSameLines("" +
                      "Maven run\n" +
                      " \"C:\\Program Files\\Java\\jdk-11.0.2\\bin\\java.exe\" -Dmaven.multiModuleProjectDirectory=C:\\dev\\idea\\testprojects\\maven-2-err -Dmaven.home=C:\\dev\\applications\\apache-maven-3.3.9 -Dclassworlds.conf=C:\\dev\\applications\\apache-maven-3.3.9\\bin\\m2.conf -Dmaven.ext.class.path=C:\\dev\\idea\\intellij\\out\\classes\\artifacts\\maven_event_listener\\maven-event-listener.jar -Didea.launcher.port=60694 -Didea.launcher.bin.path=C:\\dev\\idea\\intellij\\community\\bin\\win -Dfile.encoding=UTF-8 -classpath C:\\dev\\applications\\apache-maven-3.3.9\\boot\\plexus-classworlds-2.5.2.jar;C:\\dev\\idea\\intellij\\out\\classes\\production\\intellij.java.rt com.intellij.rt.execution.application.AppMainV2 org.codehaus.classworlds.Launcher -Didea.version2019.2 -Dmaven.repo.local=C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest install\n" +
                      " [INFO] Scanning for projects...\n" +
                      " [INFO] ------------------------------------------------------------------------\n" +
                      " [INFO] Reactor Build Order:\n" +
                      " [INFO]\n" +
                      " [INFO] project\n" +
                      " [INFO] m1\n" +
                      " [INFO] m2\n" +
                      " test:project:pom:1\n" +
                      "  [INFO]\n" +
                      "  [INFO] ------------------------------------------------------------------------\n" +
                      "  [INFO] Building project 1\n" +
                      "  [INFO] ------------------------------------------------------------------------\n" +
                      "  install\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-install-plugin:2.4:install (default-install) @ project ---\n" +
                      "   [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\pom.xml to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\project\\1\\project-1.pom\n" +
                      " test:m1:jar:1\n" +
                      "  [INFO]\n" +
                      "  [INFO] ------------------------------------------------------------------------\n" +
                      "  [INFO] Building m1 1\n" +
                      "  [INFO] ------------------------------------------------------------------------\n" +
                      "  resources\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ m1 ---\n" +
                      "   [WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources, i.e. build is platform dependent!\n" +
                      "   [INFO] skip non existing resourceDirectory C:\\dev\\idea\\testprojects\\maven-2-err\\m1\\src\\main\\resources\n" +
                      "  compile\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-compiler-plugin:3.8.0:compile (default-compile) @ m1 ---\n" +
                      "   [INFO] Nothing to compile - all classes are up to date\n" +
                      "  testResources\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-resources-plugin:2.6:testResources (default-testResources) @ m1 ---\n" +
                      "   [WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources, i.e. build is platform dependent!\n" +
                      "   [INFO] skip non existing resourceDirectory C:\\dev\\idea\\testprojects\\maven-2-err\\m1\\src\\test\\resources\n" +
                      "  testCompile\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-compiler-plugin:3.8.0:testCompile (default-testCompile) @ m1 ---\n" +
                      "   [INFO] Nothing to compile - all classes are up to date\n" +
                      "  test\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-surefire-plugin:2.12.4:test (default-test) @ m1 ---\n" +
                      "  jar\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-jar-plugin:2.4:jar (default-jar) @ m1 ---\n" +
                      "  install\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-install-plugin:2.4:install (default-install) @ m1 ---\n" +
                      "   [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\m1\\target\\m1-1.jar to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\m1\\1\\m1-1.jar\n" +
                      "   [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\m1\\pom.xml to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\m1\\1\\m1-1.pom\n" +
                      " test:m2:jar:1\n" +
                      "  [INFO]\n" +
                      "  [INFO] ------------------------------------------------------------------------\n" +
                      "  [INFO] Building m2 1\n" +
                      "  [INFO] ------------------------------------------------------------------------\n" +
                      "  resources\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ m2 ---\n" +
                      "   [WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources, i.e. build is platform dependent!\n" +
                      "   [INFO] skip non existing resourceDirectory C:\\dev\\idea\\testprojects\\maven-2-err\\m2\\src\\main\\resources\n" +
                      "  compile\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-compiler-plugin:3.8.0:compile (default-compile) @ m2 ---\n" +
                      "   [INFO] Nothing to compile - all classes are up to date\n" +
                      "  testResources\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-resources-plugin:2.6:testResources (default-testResources) @ m2 ---\n" +
                      "   [WARNING] Using platform encoding (UTF-8 actually) to copy filtered resources, i.e. build is platform dependent!\n" +
                      "   [INFO] skip non existing resourceDirectory C:\\dev\\idea\\testprojects\\maven-2-err\\m2\\src\\test\\resources\n" +
                      "  testCompile\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-compiler-plugin:3.8.0:testCompile (default-testCompile) @ m2 ---\n" +
                      "   [INFO] Nothing to compile - all classes are up to date\n" +
                      "  test\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-surefire-plugin:2.12.4:test (default-test) @ m2 ---\n" +
                      "   [INFO] No tests to run.\n" +
                      "  jar\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-jar-plugin:2.4:jar (default-jar) @ m2 ---\n" +
                      "  install\n" +
                      "   [INFO]\n" +
                      "   [INFO] --- maven-install-plugin:2.4:install (default-install) @ m2 ---\n" +
                      "   [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\m2\\target\\m2-1.jar to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\m2\\1\\m2-1.jar\n" +
                      "   [INFO] Installing C:\\dev\\idea\\testprojects\\maven-2-err\\m2\\pom.xml to C:\\Users\\alexandr.bubenchikov\\.m2\\repositoryTest\\test\\m2\\1\\m2-1.pom\n" +
                      " [INFO] ------------------------------------------------------------------------\n" +
                      " [INFO] Reactor Summary:\n" +
                      " [INFO]\n" +
                      " [INFO] project ............................................ SUCCESS [  0.410 s]\n" +
                      " [INFO] m1 ................................................. SUCCESS [  1.382 s]\n" +
                      " [INFO] m2 ................................................. SUCCESS [  0.062 s]\n" +
                      " [INFO] ------------------------------------------------------------------------\n" +
                      " [INFO] BUILD SUCCESS",
                      testCase(*fromFile("org/jetbrains/maven/buildlogs/Project2Modules.log"))
                        .runAndFormatToString())
    }
  }
}
