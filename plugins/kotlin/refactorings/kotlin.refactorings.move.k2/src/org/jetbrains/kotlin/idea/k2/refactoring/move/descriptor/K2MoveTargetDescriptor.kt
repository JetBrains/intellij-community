// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor

import com.intellij.ide.util.DirectoryUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.kotlin.idea.base.psi.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefixOrRoot
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.getOrCreateKotlinFile
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.withChildDeclarations
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

sealed interface K2MoveTargetDescriptor {
    /**
     * Either the target directory where the file or element will be moved to or the closest possible directory. The real target directory
     * can be created by calling `getOrCreateTarget`.
     */
    val baseDirectory: PsiDirectory

    val pkgName: FqName


    /**
     * Gets or creates the target
     */
    @RequiresWriteLock
    fun getOrCreateTarget(dirStructureMatchesPkg: Boolean): PsiElement

    open class Directory(
        override val pkgName: FqName,
        override val baseDirectory: PsiDirectory
    ) : K2MoveTargetDescriptor {
        override fun getOrCreateTarget(dirStructureMatchesPkg: Boolean): PsiFileSystemItem {
            if (!dirStructureMatchesPkg) return baseDirectory
            val implicitPkgPrefix = baseDirectory.getFqNameWithImplicitPrefixOrRoot()
            val pkgSuffix = pkgName.asString().removePrefix(implicitPkgPrefix.asString()).removePrefix(".")
            val file = VfsUtilCore.findRelativeFile(pkgSuffix.replace('.', java.io.File.separatorChar), baseDirectory.virtualFile)
            if (file != null) return file.toPsiDirectory(baseDirectory.project) ?: error("Could not find directory $pkgName")
            return DirectoryUtil.createSubdirectories(pkgSuffix, baseDirectory, ".")
        }
    }

    sealed interface Declaration<T: KtElement> : K2MoveTargetDescriptor {
        override fun getOrCreateTarget(dirStructureMatchesPkg: Boolean): T

        /**
         * Returns the target if it exists already.
         */
        fun getTarget(): T?

        fun addElement(target: T, element: PsiElement): PsiElement

        enum class DeclarationTargetType {
            CLASS, OBJECT, COMPANION_OBJECT, FILE
        }

        fun getTargetType(): DeclarationTargetType

        /**
         * Adds all the [elements] to this target.
         * If the target element does not exist yet, it will be created by this function (see [getOrCreateTarget])
         * Returns a map of old declarations to their new counterparts.
         * Note: the [elements] are _not_ removed from the source.
         */
        fun addElementsToTarget(elements: Collection<PsiElement>, dirStructureMatchesPkg: Boolean): Map<KtNamedDeclaration, KtNamedDeclaration> {
            val target = getOrCreateTarget(dirStructureMatchesPkg)
            val oldToNewMap = mutableMapOf<KtNamedDeclaration, KtNamedDeclaration>()
            elements.forEach { oldElement ->
                val movedElement = addElement(target, oldElement)
                if (movedElement is KtNamedDeclaration) {
                    // we assume that the children are in the same order before and after the move
                    for ((oldChild, newChild) in oldElement.withChildDeclarations().zip(movedElement.withChildDeclarations())) {
                        oldToNewMap[oldChild] = newChild
                    }
                }
            }
            return oldToNewMap
        }
    }

    class File(
        val fileName: String,
        pkgName: FqName,
        baseDirectory: PsiDirectory
    ) : Directory(pkgName, baseDirectory), Declaration<KtFile> {
        override fun getOrCreateTarget(dirStructureMatchesPkg: Boolean): KtFile {
            val directory = super.getOrCreateTarget(dirStructureMatchesPkg) as PsiDirectory
            return getOrCreateKotlinFile(fileName, directory, pkgName.asString())
        }

        override fun getTarget(): KtFile? {
            return baseDirectory.findFile(fileName) as? KtFile
        }

        override fun addElement(target: KtFile, element: PsiElement): PsiElement {
            return target.add(element)
        }

        override fun getTargetType(): Declaration.DeclarationTargetType = Declaration.DeclarationTargetType.FILE
    }

    abstract class ClassBody<T: KtClassOrObject>(targetClass: KtClassOrObject) : Declaration<T>  {
        override val baseDirectory: PsiDirectory = targetClass.containingKtFile.containingDirectory!!
        override val pkgName: FqName = targetClass.containingKtFile.packageFqName

        override fun addElement(target: T, element: PsiElement): PsiElement {
            return target.addElementToClassBody(element)
        }
    }

    class CompanionObject(
        private val containingClass: KtClass
    ) : ClassBody<KtObjectDeclaration>(containingClass) {
        override val baseDirectory: PsiDirectory = containingClass.containingKtFile.containingDirectory!!
        override val pkgName: FqName = containingClass.containingKtFile.packageFqName

        override fun getTargetType(): Declaration.DeclarationTargetType = Declaration.DeclarationTargetType.COMPANION_OBJECT

        override fun getOrCreateTarget(dirStructureMatchesPkg: Boolean): KtObjectDeclaration {
            containingClass.companionObjects.firstOrNull()?.let { return it }
            return containingClass.getOrCreateCompanionObject()
        }

        override fun getTarget(): KtObjectDeclaration? {
            return containingClass.companionObjects.firstOrNull()
        }
    }

    class ClassOrObject(
        private val classOrObject: KtClassOrObject
    ) : ClassBody<KtClassOrObject>(classOrObject) {
        override val baseDirectory: PsiDirectory = classOrObject.containingKtFile.containingDirectory!!
        override val pkgName: FqName = classOrObject.containingKtFile.packageFqName

        override fun getTargetType(): Declaration.DeclarationTargetType {
            if (classOrObject is KtObjectDeclaration) {
                if (classOrObject.isCompanion()) {
                    return Declaration.DeclarationTargetType.COMPANION_OBJECT
                } else {
                    return Declaration.DeclarationTargetType.OBJECT
                }
            }
            return Declaration.DeclarationTargetType.CLASS
        }

        override fun getTarget(): KtClassOrObject {
            return classOrObject
        }

        override fun getOrCreateTarget(dirStructureMatchesPkg: Boolean): KtClassOrObject = classOrObject
    }

    companion object {
        fun Directory(directory: PsiDirectory): Directory {
            return Directory(directory.getFqNameWithImplicitPrefixOrRoot(), directory)
        }

        fun File(file: KtFile): File {
            val directory = file.containingDirectory ?: error("No containing directory was found")
            return File(file.name, file.packageFqName, directory)
        }

        private fun KtClassOrObject.addElementToClassBody(element: PsiElement): PsiElement {
            val body = getOrCreateBody()
            val anchor = (body.rBrace ?: body.lastChild!!).prevSibling
            return if (anchor?.nextSibling is PsiErrorElement) {
                body.addBefore(element, anchor)
            } else {
                body.addAfter(element, anchor)
            }
        }
    }
}