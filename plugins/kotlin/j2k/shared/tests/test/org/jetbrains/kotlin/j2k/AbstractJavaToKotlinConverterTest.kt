// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.io.File

abstract class AbstractJavaToKotlinConverterTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = J2K_PROJECT_DESCRIPTOR

    override fun setUp() {
        super.setUp()
        addJavaLangRecordClass()
    }

    protected fun addFile(fileName: String, dirName: String? = null) {
        addFile(File(KotlinRoot.DIR, "j2k/shared/tests/testData/$fileName"), dirName)
    }

    protected fun addFile(file: File, dirName: String? = null): VirtualFile {
        return addFile(FileUtil.loadFile(file, true), file.name, dirName)
    }

    protected fun addFile(text: String, fileName: String, dirName: String?): VirtualFile {
        val filePath = (if (dirName != null) "$dirName/" else "") + fileName
        return myFixture.addFileToProject(filePath, text).virtualFile
    }

    protected fun deleteFile(virtualFile: VirtualFile) {
        runWriteAction { virtualFile.delete(this) }
    }

    // Needed to make the Kotlin compiler think it is running on JDK 16+
    // see org.jetbrains.kotlin.resolve.jvm.checkers.JvmRecordApplicabilityChecker
    private fun addJavaLangRecordClass() {
        myFixture.addClass(
            """
            package java.lang;
            public abstract class Record {}
            """.trimIndent()
        )
    }

    protected fun addJpaAnnotations() {
        for (pkg in listOf("javax.persistence", "jakarta.persistence")) {
            for (name in listOf("Column", "Embedded", "GeneratedValue", "Id", "OneToMany", "Transient", "Version")) {
                myFixture.addClass(
                    """
                    package $pkg;
                    
                    import java.lang.annotation.Target;
                    import static java.lang.annotation.ElementType.FIELD;
                    import static java.lang.annotation.ElementType.METHOD;
                    
                    @Target({METHOD, FIELD})
                    public @interface $name {}
                    """.trimIndent()
                )
            }
            for (name in listOf("Embeddable", "Entity", "MappedSuperclass")) {
                myFixture.addClass(
                    """
                    package $pkg;
                    
                    import java.lang.annotation.Target;
                    import static java.lang.annotation.ElementType.TYPE;
                    
                    @Target(TYPE)
                    public @interface $name {}
                    """.trimIndent()
                )
            }
        }
    }

    protected fun addLombokAnnotations() {
        myFixture.addClass(
            """
            package lombok;
            
            import java.lang.annotation.Target;
            import static java.lang.annotation.ElementType.TYPE;
            
            @Target(TYPE)
            public @interface RequiredArgsConstructor {
                String staticName() default "";
            }
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package lombok;
            
            import java.lang.annotation.Target;
            import static java.lang.annotation.ElementType.TYPE;
            
            @Target(TYPE)
            public @interface Data {
                String staticConstructor() default "";
            }
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package lombok;
            
            import java.lang.annotation.Target;
            import static java.lang.annotation.ElementType.TYPE;
            
            @Target(TYPE)
            public @interface NoArgsConstructor {
                String staticName() default "";
                boolean force() default false;
            }
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package lombok;
            
            import java.lang.annotation.Target;
            import static java.lang.annotation.ElementType.FIELD;
            import static java.lang.annotation.ElementType.METHOD;
            import static java.lang.annotation.ElementType.PARAMETER;
            
            @Target({FIELD, METHOD, PARAMETER})
            public @interface NonNull {}
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package lombok;
            
            public enum AccessLevel {
                PUBLIC, MODULE, PROTECTED, PACKAGE, PRIVATE, NONE
            }
            """.trimIndent()
        )
        val logAnnotations = listOf(
            "lombok.extern.apachecommons" to "CommonsLog",
            "lombok.extern.flogger" to "Flogger",
            "lombok.extern.java" to "Log",
            "lombok.extern.jbosslog" to "JBossLog",
            "lombok.extern.log4j" to "Log4j",
            "lombok.extern.log4j" to "Log4j2",
            "lombok.extern.slf4j" to "Slf4j",
            "lombok.extern.slf4j" to "XSlf4j",
        )
        for ((pkg, name) in logAnnotations) {
            myFixture.addClass(
                """
                package $pkg;
                
                import java.lang.annotation.Target;
                import static java.lang.annotation.ElementType.TYPE;
                
                @Target(TYPE)
                public @interface $name {}
                """.trimIndent()
            )
        }
        myFixture.addClass(
            """
            package lombok.experimental;
            
            import java.lang.annotation.Target;
            import static java.lang.annotation.ElementType.TYPE;
            
            @Target(TYPE)
            public @interface SuperBuilder {}
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package lombok;
            
            import java.lang.annotation.Target;
            import static java.lang.annotation.ElementType.TYPE;
            import static java.lang.annotation.ElementType.METHOD;
            import static java.lang.annotation.ElementType.CONSTRUCTOR;
            
            @Target({TYPE, METHOD, CONSTRUCTOR})
            public @interface Builder {}
            """.trimIndent()
        )
        for (name in listOf("Value", "AllArgsConstructor")) {
            myFixture.addClass(
                """
                package lombok;
                
                import java.lang.annotation.Target;
                import static java.lang.annotation.ElementType.TYPE;
                
                @Target(TYPE)
                public @interface $name {
                    String staticConstructor() default "";
                }
                """.trimIndent()
            )
        }
        for (name in listOf("EqualsAndHashCode", "ToString")) {
            myFixture.addClass(
                """
                package lombok;
                
                import java.lang.annotation.Target;
                import static java.lang.annotation.ElementType.TYPE;
                
                @Target(TYPE)
                public @interface $name {}
                """.trimIndent()
            )
        }
        for (name in listOf("Getter", "Setter")) {
            myFixture.addClass(
                """
                package lombok;
                
                import java.lang.annotation.Target;
                import static java.lang.annotation.ElementType.FIELD;
                import static java.lang.annotation.ElementType.TYPE;
                
                @Target({FIELD, TYPE})
                public @interface $name {
                    AccessLevel value() default AccessLevel.PUBLIC;
                }
                """.trimIndent()
            )
        }
    }

    protected fun addJunitTestAnnotations() {
        myFixture.addClass(
            """
            package org.junit;
            
            public @interface Test {}
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package org.junit.jupiter.api;
            
            public @interface Test {}
            """.trimIndent()
        )
    }
}