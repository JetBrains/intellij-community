// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.jps.builders.java.dependencyView

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.jetbrains.jps.builders.java.dependencyView.TypeRepr.ClassType
import org.jetbrains.kotlin.fileClasses.internalNameWithoutInnerClasses
import org.jetbrains.kotlin.load.java.JAVAX_NONNULL_ANNOTATION
import org.jetbrains.kotlin.load.java.NOT_NULL_ANNOTATIONS
import org.jetbrains.kotlin.load.java.NULLABLE_ANNOTATIONS
import java.util.*

internal class NullabilityAnnotationsTracker : AnnotationsChangeTracker() {
    private val annotations =
        (NULLABLE_ANNOTATIONS + JAVAX_NONNULL_ANNOTATION + NOT_NULL_ANNOTATIONS).mapTo(HashSet()) { it.internalNameWithoutInnerClasses }
            .toTypedArray()

    override fun methodAnnotationsChanged(
        context: DependencyContext,
        method: MethodRepr,
        annotationsDiff: Difference.Specifier<ClassType, Difference>,
        paramAnnotationsDiff: Difference.Specifier<ParamAnnotation, Difference>
    ): Set<Recompile> {
        val changedAnnotations = annotationsDiff.addedOrRemoved() +
                paramAnnotationsDiff.addedOrRemoved().map { it.type }

        return handleNullAnnotationsChanges(context, method, changedAnnotations)
    }

    override fun fieldAnnotationsChanged(
        context: NamingContext,
        field: FieldRepr,
        annotationsDiff: Difference.Specifier<ClassType, Difference>
    ): Set<Recompile> {
        return handleNullAnnotationsChanges(context, field, annotationsDiff.addedOrRemoved())
    }

    private fun handleNullAnnotationsChanges(
        context: NamingContext,
        protoMember: ProtoMember,
        annotations: Sequence<TypeRepr.ClassType>
    ): Set<Recompile> {
        val n = this.annotations.size
        val nullabilityAnnotations = IntOpenHashSet(n)
        for (i in 0 until n) {
            nullabilityAnnotations.add(context.get(this.annotations.get(i)))
        }

        val changedNullAnnotation = annotations.firstOrNull { nullabilityAnnotations.contains(it.className) }

        val result = EnumSet.noneOf(Recompile::class.java)
        if (changedNullAnnotation != null) {
            result.add(Recompile.USAGES)

            if (protoMember is MethodRepr && !protoMember.isFinal) {
                // methods can be overridden, whereas fields cannot be
                result.add(Recompile.SUBCLASSES)
            }
        }

        return result
    }

    private fun <T> Difference.Specifier<T, Difference>.addedOrRemoved(): Sequence<T> =
        added().asSequence() + removed().asSequence()

}