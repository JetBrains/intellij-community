/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.annotator

/**
 * Launch annotation test in batch mode.
 *
 * See [com.intellij.lang.annotation.AnnotationHolder.isBatchMode]
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class BatchMode
