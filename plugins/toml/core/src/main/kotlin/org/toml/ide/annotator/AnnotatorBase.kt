/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.annotator

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly

abstract class AnnotatorBase : Annotator {

    final override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!ApplicationManager.getApplication().isUnitTestMode || javaClass in enabledAnnotators) {
            annotateInternal(element, holder)
        }
    }

    protected abstract fun annotateInternal(element: PsiElement, holder: AnnotationHolder)

    companion object {
        private val enabledAnnotators: MutableSet<Class<out AnnotatorBase>> = ConcurrentCollectionFactory.createConcurrentSet()

        @TestOnly
        fun enableAnnotator(annotatorClass: Class<out AnnotatorBase>, parentDisposable: Disposable) {
            enabledAnnotators += annotatorClass
            Disposer.register(parentDisposable) { enabledAnnotators -= annotatorClass }
        }
    }
}
