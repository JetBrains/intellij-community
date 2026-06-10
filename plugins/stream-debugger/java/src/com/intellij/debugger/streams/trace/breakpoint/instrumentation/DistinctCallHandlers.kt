// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.intellij.debugger.streams.java.rt.KeyRecorder
import com.intellij.debugger.streams.java.rt.EntryKeyCapturingWrapper
import com.intellij.debugger.streams.java.rt.StreamDebuggerUtils
import com.intellij.debugger.streams.java.rt.collectors.UniversalCollector
import com.sun.jdi.ArrayReference
import com.sun.jdi.ClassType
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * Abstract base for all distinct-operation handlers.
 * Provides the common [result] implementation; subclasses only supply [computeMapping].
 */
internal abstract class DistinctPeekCallHandler(
  objectStorage: ObjectStorage,
  private val typeBefore: GenericType?,
  private val typeAfter: GenericType?,
  time: ObjectReference,
) : PeekCallHandler(objectStorage, typeBefore, typeAfter, time) {

  override fun result(evaluationContextImpl: EvaluationContextImpl): Value {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return objectStorage.watch(evaluationContextImpl) {
      val beforeFormatted = formatMap(beforeValuesMap, typeBefore)
      val afterFormatted = formatMap(afterValuesMap, typeAfter)
      array(array(beforeFormatted, afterFormatted), computeMapping())
    }
  }

  protected abstract fun ValueContext.computeMapping(): ArrayReference
}

/**
 * Handler for `distinct()` — uses the recorded before/after maps to detect duplicates.
 */
internal class DistinctCallHandler(
  objectStorage: ObjectStorage,
  typeBefore: GenericType?,
  typeAfter: GenericType?,
  time: ObjectReference,
) : DistinctPeekCallHandler(objectStorage, typeBefore, typeAfter, time) {

  override fun ValueContext.computeMapping(): ArrayReference {
    val before = beforeValuesMap
    val after = afterValuesMap
    if (before == null || after == null) {
      return array(array("int", 0), array("int", 0))
    }
    val utilsClass = clazz(StreamDebuggerUtils::class.java)
    val method = utilsClass.method("computeDistinctMapping", "(Ljava/util/Map;Ljava/util/Map;)[Ljava/lang/Object;")
    return method.invoke(utilsClass, listOf(before, after)) as ArrayReference
  }
}

/**
 * Handler for StreamEx `distinct(keyExtractor)` — wraps the key extractor in a keyExtractor
 * that records each computed key in order, then uses the captured keys to build the
 * equivalence-class mapping. This avoids re-applying the extractor after execution,
 * which would produce wrong results for stateful extractors.
 */
internal class DistinctByKeyCallHandler(
  objectStorage: ObjectStorage,
  typeBefore: GenericType?,
  typeAfter: GenericType?,
  time: ObjectReference,
) : DistinctPeekCallHandler(objectStorage, typeBefore, typeAfter, time) {

  // KeyRecorder created during transformArguments; records each computed key in order.
  private var keyExtractor: ObjectReference? = null

  override fun transformArguments(
    evaluationContextImpl: EvaluationContextImpl,
    method: Method,
    arguments: List<Value?>,
  ): List<Value?> {
    val original = arguments.firstOrNull() as? ObjectReference ?: return arguments
    val wrapper = objectStorage.watch(evaluationContextImpl) {
      // Use instance() so ClassLoadingUtils explicitly loads KeyRecorder into the target VM
      // (it is not loaded automatically when StreamDebuggerUtils is loaded).
      instance(KeyRecorder::class.java, "(Ljava/util/function/Function;)V", listOf(original))
    }
    keyExtractor = wrapper
    return listOf(wrapper) + arguments.drop(1)
  }

  override fun ValueContext.computeMapping(): ArrayReference {
    val before = beforeValuesMap
    val after = afterValuesMap
    val keyExtractorRef = keyExtractor
    if (before == null || after == null || keyExtractorRef == null) {
      return array(array("int", 0), array("int", 0))
    }
    val utilsClass = clazz(StreamDebuggerUtils::class.java)
    val capturedKeysField = DebuggerUtils.findField(keyExtractorRef.referenceType(), "capturedKeys")
      ?: return array(array("int", 0), array("int", 0))
    val capturedKeysList = keyExtractorRef.getValue(capturedKeysField) as ObjectReference
    val method = utilsClass.method(
      "computeDistinctByRecordedKeyMapping",
      "(Ljava/util/Map;Ljava/util/Map;Ljava/util/List;)[Ljava/lang/Object;"
    )
    return method.invoke(utilsClass, listOf(before, after, capturedKeysList)) as ArrayReference
  }
}

/**
 * Handler for StreamEx `distinctKeys()`/`distinctValues()` — wraps each Map.Entry in an
 * [EntryKeyCapturingWrapper] before the distinct operation so that the key/value accessed by
 * the operation is recorded without re-invoking `getKey()`/`getValue()` post-execution.
 * This prevents triggering side effects in user-defined entry implementations.
 */
internal class DistinctByMapEntryCallHandler(
  objectStorage: ObjectStorage,
  typeBefore: GenericType?,
  typeAfter: GenericType?,
  time: ObjectReference,
  private val byKey: Boolean,
) : DistinctPeekCallHandler(objectStorage, typeBefore, typeAfter, time) {

  private var factory: ObjectReference? = null

  override fun beforeCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    if (value !is ObjectReference) return value
    return objectStorage.watch(evaluationContextImpl) {
      // 1. factory = EntryKeyCapturingWrapper.keys() or .values() — accumulates capturedKeys
      val entryWrapperClass = clazz(EntryKeyCapturingWrapper::class.java)
      val factoryMethod = entryWrapperClass.method(
        if (byKey) "keys" else "values",
        "()Lcom/intellij/debugger/streams/java/rt/EntryKeyCapturingWrapper;"
      )
      val factoryInstance = factoryMethod.invoke(entryWrapperClass, emptyList()) as ObjectReference
      factory = factoryInstance

      // 2. entryStream.map(factory) → Stream<EntryKeyCapturingWrapper>
      val streamType = value.referenceType() as ClassType
      val mapMethod = streamType.method("map", "(Ljava/util/function/Function;)Ljava/util/stream/Stream;")
      val mappedStream = mapMethod.invoke(value, listOf(factoryInstance)) as ObjectReference

      // 3. peek(beforeCollector) on the plain Stream<Wrapper> (tick=false: reads current time)
      val valuesMap = instance(LinkedHashMap::class.java)
      beforeValuesMap = valuesMap
      val collector = instance(
        UniversalCollector::class.java,
        UNIVERSAL_COLLECTOR_CONSTRUCTOR_SIGNATURE,
        listOf(valuesMap, time, false.mirror),
      )
      val mappedStreamType = mappedStream.referenceType() as ClassType
      val peekMethod = mappedStreamType.method("peek", "(Ljava/util/function/Consumer;)Ljava/util/stream/Stream;")
      val peekedStream = peekMethod.invoke(mappedStream, listOf(collector)) as ObjectReference

      // 4. EntryStream.of(peekedStream) — convert back so distinctKeys()/distinctValues() sees an EntryStream
      val entryStreamClass = clazz("one.util.streamex.EntryStream")
      val ofMethod = entryStreamClass.method("of", "(Ljava/util/stream/Stream;)Lone/util/streamex/EntryStream;")
      ofMethod.invoke(entryStreamClass, listOf(peekedStream)) as ObjectReference
    }
  }

  override fun ValueContext.computeMapping(): ArrayReference {
    val before = beforeValuesMap
    val after = afterValuesMap
    val factoryRef = factory
    if (before == null || after == null || factoryRef == null) return array(array("int", 0), array("int", 0))
    val utilsClass = clazz(StreamDebuggerUtils::class.java)
    val capturedKeysField = DebuggerUtils.findField(factoryRef.referenceType(), "capturedKeys")
      ?: return array(array("int", 0), array("int", 0))
    val capturedKeysList = factoryRef.getValue(capturedKeysField) as ObjectReference
    val method = utilsClass.method(
      "computeDistinctByRecordedKeyMapping",
      "(Ljava/util/Map;Ljava/util/Map;Ljava/util/List;)[Ljava/lang/Object;",
    )
    return method.invoke(utilsClass, listOf(before, after, capturedKeysList)) as ArrayReference
  }
}
