// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.modules;

import junit.framework.TestCase;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.kotlin.build.JvmSourceRoot;
import org.jetbrains.kotlin.idea.test.TestUtilsKt;
import org.jetbrains.kotlin.test.KotlinTestUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

public class KotlinModuleXmlGeneratorTest extends TestCase {
    private static String getTestDataPath() {
        return TestUtilsKt.IDEA_TEST_DATA_DIR.getAbsolutePath() + "/modules.xml";
    }

    public void testBasic() {
        String actual = new KotlinModuleXmlBuilder().addModule(
                "name",
                "output",
                Arrays.asList(new File("s1"), new File("s2")),
                Collections.singletonList(new JvmSourceRoot(new File("java"), null)),
                Arrays.asList(new File("cp1"), new File("cp2")),
                Collections.emptyList(),
                null,
                JavaModuleBuildTargetType.PRODUCTION.getTypeId(),
                JavaModuleBuildTargetType.PRODUCTION.isTests(),
                Collections.emptySet(),
                Collections.emptyList()
        ).asText().toString();
        KotlinTestUtils.assertEqualsToFile(new File(getTestDataPath() + "/basic.xml"), actual);
    }

    public void testFiltered() {
        String actual = new KotlinModuleXmlBuilder().addModule(
                "name",
                "output",
                Arrays.asList(new File("s1"), new File("s2")),
                Collections.emptyList(),
                Arrays.asList(new File("cp1"), new File("cp2")),
                Collections.emptyList(),
                null,
                JavaModuleBuildTargetType.PRODUCTION.getTypeId(),
                JavaModuleBuildTargetType.PRODUCTION.isTests(),
                Collections.singleton(new File("cp1")),
                Collections.emptyList()
        ).asText().toString();
        KotlinTestUtils.assertEqualsToFile(new File(getTestDataPath() + "/filtered.xml"), actual);
    }

    public void testMultiple() {
        KotlinModuleXmlBuilder builder = new KotlinModuleXmlBuilder();
        builder.addModule(
                "name",
                "output",
                Arrays.asList(new File("s1"), new File("s2")),
                Collections.emptyList(),
                Arrays.asList(new File("cp1"), new File("cp2")),
                Collections.emptyList(),
                null,
                JavaModuleBuildTargetType.PRODUCTION.getTypeId(),
                JavaModuleBuildTargetType.PRODUCTION.isTests(),
                Collections.singleton(new File("cp1")),
                Collections.emptyList()
        );
        builder.addModule(
                "name2",
                "output2",
                Arrays.asList(new File("s12"), new File("s22")),
                Collections.emptyList(),
                Arrays.asList(new File("cp12"), new File("cp22")),
                Collections.emptyList(),
                null,
                JavaModuleBuildTargetType.TEST.getTypeId(),
                JavaModuleBuildTargetType.TEST.isTests(),
                Collections.singleton(new File("cp12")),
                Collections.emptyList()
        );
        String actual = builder.asText().toString();
        KotlinTestUtils.assertEqualsToFile(new File(getTestDataPath() + "/multiple.xml"), actual);
    }

    public void testModularJdkRoot() {
        String actual = new KotlinModuleXmlBuilder().addModule(
                "name",
                "output",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                new File("/path/to/modular/jdk"),
                JavaModuleBuildTargetType.PRODUCTION.getTypeId(),
                JavaModuleBuildTargetType.PRODUCTION.isTests(),
                Collections.emptySet(),
                Collections.emptyList()
        ).asText().toString();
        KotlinTestUtils.assertEqualsToFile(new File(getTestDataPath() + "/modularJdkRoot.xml"), actual);
    }
}
