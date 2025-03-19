// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UNINITIALIZED_UAST_PART
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.kotlin.internal.KotlinUElementWithComments

@ApiStatus.Internal
abstract class KotlinAbstractUElement(
    givenParent: UElement?,
) : KotlinUElementWithComments {

    private var uastParentPart: Any? = givenParent ?: UNINITIALIZED_UAST_PART

    protected val languagePlugin: UastLanguagePlugin?
        get() {
            return psi?.let { UastFacade.findPlugin(it) }
        }

    val baseResolveProviderService: BaseKotlinUastResolveProviderService
        get() {
            return ApplicationManager.getApplication().getService(BaseKotlinUastResolveProviderService::class.java)
                ?: error("${BaseKotlinUastResolveProviderService::class.java.name} is not available for ${this::class.simpleName}")
        }

    final override val uastParent: UElement?
        get() {
            if (uastParentPart == UNINITIALIZED_UAST_PART) {
                uastParentPart = convertParent()
            }

            return uastParentPart as UElement?
        }

    protected open fun convertParent(): UElement? {
        return baseResolveProviderService.convertParent(this)
    }

    override fun asSourceString(): String {
        return sourcePsi?.text ?: super.asSourceString()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is UElement) {
            return false
        }

        // See https://youtrack.jetbrains.com/issue/KTIJ-9793 for more details.
        if (this.psi == other.psi) {
            if (this.psi == null) {
                // Two UElements can be different but both have null PSI fields; in that case, do a deeper check
                if (this === other) { // same instance: always equal
                    return true
                }
                if (this.javaClass !== other.javaClass) { // different types: never equal
                    return false
                }
                return this.asSourceString() == other.asSourceString() // source code equality
            }
            return true
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        return psi?.hashCode() ?: 0
    }
}
