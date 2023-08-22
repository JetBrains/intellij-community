// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.config.JvmTarget.Companion.fromString
import org.jetbrains.kotlin.idea.checkers.ENABLE_JVM_PREVIEW
import org.jetbrains.kotlin.idea.checkers.parseLanguageVersionSettings
import org.jetbrains.kotlin.idea.test.testFramework.KtUsefulTestCase
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.TestJdkKind
import java.io.File
import java.lang.reflect.Field
import java.util.*
import java.util.regex.Pattern

abstract class KotlinBaseTest<F : KotlinBaseTest.TestFile> : KtUsefulTestCase() {
    @Throws(java.lang.Exception::class)
    protected open fun doTest(filePath: String) {
        val file = File(filePath)
        val expectedText = KotlinTestUtils.doLoadFile(file)
        doMultiFileTest(file, createTestFilesFromFile(file, expectedText))
    }

    protected abstract fun createTestFilesFromFile(file: File, expectedText: String): List<F>

    @Throws(java.lang.Exception::class)
    protected open fun doMultiFileTest(
        wholeFile: File,
        files: List<F>
    ) {
        throw UnsupportedOperationException("Multi-file test cases are not supported in this test")
    }

    protected open fun updateConfiguration(configuration: CompilerConfiguration) {}

    protected open fun setupEnvironment(environment: KotlinCoreEnvironment) {}

    protected open fun parseDirectivesPerFiles() = false

    protected open val backend = TargetBackend.ANY

    protected open fun configureTestSpecific(configuration: CompilerConfiguration, testFiles: List<TestFile>) {}

    protected fun createConfiguration(
        kind: ConfigurationKind,
        jdkKind: TestJdkKind,
        backend: TargetBackend,
        classpath: List<File?>,
        javaSource: List<File?>,
        testFilesWithConfigurationDirectives: List<TestFile>
    ): CompilerConfiguration {
        val configuration = KotlinTestUtils.newConfiguration(kind, jdkKind, classpath, javaSource)
        configuration.put(JVMConfigurationKeys.IR, backend.isIR)
        updateConfigurationByDirectivesInTestFiles(
            testFilesWithConfigurationDirectives,
            configuration,
            parseDirectivesPerFiles()
        )
        updateConfiguration(configuration)
        configureTestSpecific(configuration, testFilesWithConfigurationDirectives)
        return configuration
    }

    open class TestFile @JvmOverloads constructor(
        @JvmField val name: String,
        @JvmField val content: String,
        @JvmField val directives: Directives = Directives()
    ) : Comparable<TestFile> {
        override operator fun compareTo(other: TestFile): Int {
            return name.compareTo(other.name)
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            return other is TestFile && other.name == name
        }

        override fun toString(): String {
            return name
        }

    }

    open class TestModule(
        @JvmField val name: String,
        @JvmField val dependenciesSymbols: List<String>,
        @JvmField val friendsSymbols: List<String>
    ) : Comparable<TestModule> {

        val dependencies: MutableList<TestModule> = arrayListOf()
        val friends: MutableList<TestModule> = arrayListOf()

        override fun compareTo(other: TestModule): Int = name.compareTo(other.name)

        override fun toString(): String = name
    }

    companion object {

        private val FLAG_NAMESPACE_TO_CLASS: Map<String, Class<*>> = ImmutableMap.of(
            "CLI", CLIConfigurationKeys::class.java,
            "JVM", JVMConfigurationKeys::class.java
        )

        private val FLAG_CLASSES: List<Class<*>> = ImmutableList.of(
            CLIConfigurationKeys::class.java,
            JVMConfigurationKeys::class.java
        )

        private val BOOLEAN_FLAG_PATTERN = Pattern.compile("([+-])(([a-zA-Z_0-9]*)\\.)?([a-zA-Z_0-9]*)")

        private val ASSERTIONS_MODE_FLAG_PATTERN = Pattern.compile("ASSERTIONS_MODE=([a-zA-Z_0-9-]*)")
        private val STRING_CONCAT = Pattern.compile("STRING_CONCAT=([a-zA-Z_0-9-]*)")

        private fun tryApplyBooleanFlag(
            configuration: CompilerConfiguration,
            flag: String,
            flagEnabled: Boolean,
            flagNamespace: String?,
            flagName: String
        ) {
            val configurationKeysClass: Class<*>?
            var configurationKeyField: Field? = null
            if (flagNamespace == null) {
                for (flagClass in FLAG_CLASSES) {
                    try {
                        configurationKeyField = flagClass.getField(flagName)
                        break
                    } catch (ignored: java.lang.Exception) {
                    }
                }
            } else {
                configurationKeysClass = FLAG_NAMESPACE_TO_CLASS[flagNamespace]
                assert(configurationKeysClass != null) { "Expected [+|-][namespace.]configurationKey, got: $flag" }
                configurationKeyField = try {
                    configurationKeysClass!!.getField(flagName)
                } catch (e: java.lang.Exception) {
                    null
                }
            }
            assert(configurationKeyField != null) { "Expected [+|-][namespace.]configurationKey, got: $flag" }
            try {
                @Suppress("UNCHECKED_CAST")
                val configurationKey = configurationKeyField!![null] as CompilerConfigurationKey<Boolean>
                configuration.put(configurationKey, flagEnabled)
            } catch (e: java.lang.Exception) {
                assert(false) { "Expected [+|-][namespace.]configurationKey, got: $flag" }
            }
        }

        private fun updateConfigurationByDirectivesInTestFiles(
            testFilesWithConfigurationDirectives: List<TestFile>,
            configuration: CompilerConfiguration,
            usePreparsedDirectives: Boolean
        ) {
            var explicitLanguageVersionSettings: LanguageVersionSettings? = null
            val kotlinConfigurationFlags: MutableList<String> = ArrayList(0)
            for (testFile in testFilesWithConfigurationDirectives) {
                val content = testFile.content
                val directives = if (usePreparsedDirectives) testFile.directives else KotlinTestUtils.parseDirectives(content)
                val flags = directives.listValues("KOTLIN_CONFIGURATION_FLAGS")
                if (flags != null) {
                    kotlinConfigurationFlags.addAll(flags)
                }
                val targetString = directives["JVM_TARGET"]
                if (targetString != null) {
                    val jvmTarget = fromString(targetString)
                        ?: error("Unknown target: $targetString")
                    configuration.put(JVMConfigurationKeys.JVM_TARGET, jvmTarget)
                }

                if (directives.contains(ENABLE_JVM_PREVIEW)) {
                    configuration.put(JVMConfigurationKeys.ENABLE_JVM_PREVIEW, true)
                }

                val version = directives["LANGUAGE_VERSION"]
                if (version != null) {
                    throw AssertionError(
                        """
                    Do not use LANGUAGE_VERSION directive in compiler tests because it's prone to limiting the test
                    to a specific language version, which will become obsolete at some point and the test won't check
                    things like feature intersection with newer releases. Use `// !LANGUAGE: [+-]FeatureName` directive instead,
                    where FeatureName is an entry of the enum `LanguageFeature`
                    
                    """.trimIndent()
                    )
                }
                val fileLanguageVersionSettings: LanguageVersionSettings? = parseLanguageVersionSettings(directives)
                if (fileLanguageVersionSettings != null) {
                    assert(explicitLanguageVersionSettings == null) { "Should not specify !LANGUAGE directive twice" }
                    explicitLanguageVersionSettings = fileLanguageVersionSettings
                }
            }
            if (explicitLanguageVersionSettings != null) {
                configuration.languageVersionSettings = explicitLanguageVersionSettings
            }
            updateConfigurationWithFlags(configuration, kotlinConfigurationFlags)
        }

        private fun updateConfigurationWithFlags(configuration: CompilerConfiguration, flags: List<String>) {
            for (flag in flags) {
                var m = BOOLEAN_FLAG_PATTERN.matcher(flag)
                if (m.matches()) {
                    val flagEnabled = "-" != m.group(1)
                    val flagNamespace = m.group(3)
                    val flagName = m.group(4)
                    tryApplyBooleanFlag(configuration, flag, flagEnabled, flagNamespace, flagName)
                    continue
                }
                m = ASSERTIONS_MODE_FLAG_PATTERN.matcher(flag)
                if (m.matches()) {
                    val flagValueString = m.group(1)
                    val mode = JVMAssertionsMode.fromStringOrNull(flagValueString)
                        ?: error("Wrong ASSERTIONS_MODE value: $flagValueString")
                    configuration.put(JVMConfigurationKeys.ASSERTIONS_MODE, mode)
                }

                m = STRING_CONCAT.matcher(flag)
                if (m.matches()) {
                    val flagValueString = m.group(1)
                    val mode = JvmStringConcat.fromString(flagValueString)
                        ?: error("Wrong STRING_CONCAT value: $flagValueString")
                    configuration.put(JVMConfigurationKeys.STRING_CONCAT, mode)
                }
            }
        }
    }
}
