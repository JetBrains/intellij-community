// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.JavaPsiFacade
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.psi.KtFile

private const val COMPOSABLE_SINGLETONS_PREFIX = "ComposableSingletons"
private const val ANDROIDX_COMPOSE_PACKAGE_NAME = "androidx.compose"

/**
 * Compute the name of the ComposableSingletons class for the given file.
 *
 * Compose compiler plugin creates per-file ComposableSingletons classes to cache
 * composable lambdas without captured variables. We need to locate these classes in order
 * to search them for breakpoint locations.
 *
 * NOTE: The pattern for ComposableSingletons classes needs to be kept in sync with the
 *       code in `ComposerLambdaMemoization.getOrCreateComposableSingletonsClass`.
 *       The optimization was introduced in I8c967b14c5d9bf67e5646e60f630f2e29e006366
 */
fun computeComposableSingletonsClassName(file: KtFile): String {
    // The code in `ComposerLambdaMemoization` always uses the file short name and
    // ignores `JvmName` annotations, but (implicitly) respects `JvmPackageName`
    // annotations.
    val filePath = file.virtualFile?.path ?: file.name
    val fileName = filePath.split('/').last()
    val shortName = PackagePartClassUtils.getFilePartShortName(fileName)
    val fileClassFqName = runReadAction { JvmFileClassUtil.getFileClassInfoNoResolve(file) }.facadeClassFqName
    val classNameSuffix = "$COMPOSABLE_SINGLETONS_PREFIX\$$shortName"
    val filePackageName = fileClassFqName.parent()
    if (filePackageName.isRoot) {
        return classNameSuffix
    }
    return "${filePackageName.asString()}.$classNameSuffix"
}

fun SourcePosition.isInsideProjectWithCompose(): Boolean =
    ReadAction.nonBlocking<Boolean> {
        JavaPsiFacade.getInstance(file.project).findPackage(ANDROIDX_COMPOSE_PACKAGE_NAME) != null
    }.executeSynchronously()

fun getComposableSingletonsClasses(debugProcess: DebugProcess, file: KtFile): List<ReferenceType> {
    val vm = debugProcess.virtualMachineProxy
    val composableSingletonsClassName = computeComposableSingletonsClassName(file)
    return vm.classesByName(composableSingletonsClassName).flatMap { referenceType ->
        if (referenceType.isPrepared) vm.nestedTypes(referenceType) else listOf()
    }
}

fun getClassPrepareRequestForComposableSingletons(
    debugProcess: DebugProcess,
    requestor: ClassPrepareRequestor,
    file: KtFile
): ClassPrepareRequest? {
    return debugProcess.requestsManager.createClassPrepareRequest(
        requestor,
        "${computeComposableSingletonsClassName(file)}\$*"
    )
}
