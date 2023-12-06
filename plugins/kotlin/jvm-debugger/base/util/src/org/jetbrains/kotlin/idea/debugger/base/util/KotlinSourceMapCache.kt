// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.base.util

import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.caches.project.cacheByClass
import org.jetbrains.kotlin.codegen.inline.SMAP
import org.jetbrains.kotlin.codegen.inline.SMAPParser
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.util.caching.ConcurrentFactoryCache
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import java.io.IOException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes

@Service(Service.Level.PROJECT)
class KotlinSourceMapCache(private val project: Project) {
    companion object {
        private val LOG = Logger.getInstance(KotlinSourceMapCache::class.java)

        fun getInstance(project: Project): KotlinSourceMapCache = project.service()
    }

    fun getSourceMap(file: VirtualFile, jvmName: JvmClassName): SMAP? {
        val dependencies = arrayOf(
            PsiModificationTracker.MODIFICATION_COUNT,
            ProjectRootModificationTracker.getInstance(project)
        )

        data class Key(val path: String, val jvmName: JvmClassName)

        val cache = project.cacheByClass(KotlinSourceMapCache::class.java, *dependencies) {
            val storage = ContainerUtil.createConcurrentSoftValueMap<Key, Optional<SMAP>>()
            ConcurrentFactoryCache(storage)
        }

        val key = Key(file.path, jvmName)
        return cache.get(key) { Optional.ofNullable(findSourceMap(file, jvmName)) }.orElse(null)
    }

    private fun findSourceMap(file: VirtualFile, jvmName: JvmClassName): SMAP? {
        val bytecode = when {
            RootKindFilter.projectSources.matches(project, file) -> findCompiledModuleClass(file, jvmName)
            RootKindFilter.librarySources.matches(project, file) -> findLibraryClass(jvmName)
            else -> null
        }

        return bytecode?.let(::parseSourceMap)
    }

    private fun parseSourceMap(bytecode: ByteArray): SMAP? {
        var debugInfo: String? = null

        ClassReader(bytecode).accept(object : ClassVisitor(Opcodes.API_VERSION) {
            override fun visitSource(source: String?, debug: String?) {
                debugInfo = debug
            }
        }, ClassReader.SKIP_FRAMES and ClassReader.SKIP_CODE)

        return debugInfo?.let(SMAPParser::parseOrNull)
    }

    private fun findCompiledModuleClass(file: VirtualFile, jvmName: JvmClassName): ByteArray? {
        val module = ProjectFileIndex.getInstance(project).getModuleForFile(file) ?: return null

        findCompiledModuleClass(module, jvmName)?.let { return it }

        for (implementingModule in module.implementingModules) {
            findCompiledModuleClass(implementingModule, jvmName)?.let { return it }
        }

        return null
    }

    private fun findCompiledModuleClass(module: Module, jvmName: JvmClassName): ByteArray? {
        for (outputRoot in CompilerPaths.getOutputPaths(arrayOf(module)).toList()) {
          val path = Path.of(outputRoot, jvmName.internalName + ".class")
          if (path.isRegularFile()) {
            try {
              return path.readBytes()
            }
            catch (e: IOException) {
              LOG.debug("Can't read class file $jvmName", e)
              return null
            }
          }
        }

        return null
    }

    private fun findLibraryClass(jvmName: JvmClassName): ByteArray? {
        fun readFile(file: VirtualFile): ByteArray? {
            try {
                return file.contentsToByteArray(false)
            } catch (e: IOException) {
                LOG.debug("Can't read class file $jvmName", e)
                return null
            }
        }

        val classFileName = jvmName.internalName.substringAfterLast('/')
        val fileFinder = VirtualFileFinderFactory.getInstance(project).create(GlobalSearchScope.allScope(project))

        for (topLevelClassName in composeTopLevelClassNameVariants(jvmName)) {
            val variantClassFile = fileFinder.findVirtualFileWithHeader(ClassId.fromString(topLevelClassName))
            if (variantClassFile != null && topLevelClassName == jvmName.internalName) {
                return readFile(variantClassFile)
            }

            val packageDir = variantClassFile?.parent
            if (packageDir != null) {
                val classFile = packageDir.findChild("$classFileName.class")
                if (classFile != null) {
                    return readFile(classFile)
                }
            }
        }

        return null
    }

    // There might be classes with dollars in names (e.g. `class Foo$Bar {}`)
    private fun composeTopLevelClassNameVariants(jvmName: JvmClassName): List<String> {
        return buildList {
            val jdiName = jvmName.internalName.replace('/', '.')
            var index = jdiName.indexOf('$', startIndex = 1)
            while (index >= 0) {
                add(jdiName.take(index))
                index = jdiName.indexOf('$', startIndex = index + 1)
            }

            add(jvmName.internalName)
        }
    }
}
