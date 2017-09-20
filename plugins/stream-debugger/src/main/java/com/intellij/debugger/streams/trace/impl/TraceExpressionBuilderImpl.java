/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.lib.LibraryManager;
import com.intellij.debugger.streams.psi.impl.LambdaToAnonymousTransformer;
import com.intellij.debugger.streams.psi.impl.MethodReferenceToLambdaTransformer;
import com.intellij.debugger.streams.psi.impl.ToObjectInheritorTransformer;
import com.intellij.debugger.streams.trace.IntermediateCallHandler;
import com.intellij.debugger.streams.trace.TerminatorCallHandler;
import com.intellij.debugger.streams.trace.TraceExpressionBuilder;
import com.intellij.debugger.streams.trace.TraceHandler;
import com.intellij.debugger.streams.trace.dsl.impl.DslImpl;
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaStatementFactory;
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaTypes;
import com.intellij.debugger.streams.trace.impl.handler.PeekCall;
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.wrapper.QualifierExpression;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall;
import com.intellij.debugger.streams.wrapper.impl.StreamChainImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementFactory;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class TraceExpressionBuilderImpl implements TraceExpressionBuilder {
  private static final Logger LOG = Logger.getInstance(TraceExpressionBuilderImpl.class);

  public static final String LINE_SEPARATOR = "\n";

  private static final String RESULT_VARIABLE_NAME = "myRes";
  private static final String RESULT_VARIABLE_DECLARATION = "Object " + RESULT_VARIABLE_NAME + " = null;" + LINE_SEPARATOR;
  private static final String RESULT_EXPRESSION =
    RESULT_VARIABLE_NAME + " = new java.lang.Object[]{ info, streamResult, elapsedTime };" + LINE_SEPARATOR;

  private final Project myProject;

  public TraceExpressionBuilderImpl(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public String createTraceExpression(@NotNull StreamChain chain) {
    final LibraryManager libraryManager = LibraryManager.getInstance(myProject);
    final List<IntermediateCallHandler> intermediateHandlers = getHandlers(libraryManager, chain.getIntermediateCalls());
    final TerminatorStreamCall terminatorCall = chain.getTerminationCall();
    final TerminatorCallHandler terminatorHandler =
      libraryManager.getLibrary(terminatorCall).createHandlerFactory(new DslImpl(new JavaStatementFactory()))
      .getForTermination(terminatorCall, "evaluationResult[0]");

    final StreamChain traceChain = buildTraceChain(chain, intermediateHandlers, terminatorHandler);

    final String declarations = buildDeclarations(intermediateHandlers, terminatorHandler);

    final String fillingInfoArray = buildFillInfo(intermediateHandlers, terminatorHandler);

    final String tracingCall = buildStreamExpression(traceChain);

    final String evaluationCodeBlock = String.format("{" + LINE_SEPARATOR + "%s" + LINE_SEPARATOR + " }",
                                                     declarations + tracingCall + fillingInfoArray);
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    final String expression = ApplicationManager.getApplication().runReadAction((Computable<String>)() -> {
      final PsiCodeBlock block = elementFactory.createCodeBlockFromText(evaluationCodeBlock, chain.getContext());

      LOG.info("before transformation: " + LINE_SEPARATOR + block.getText());

      MethodReferenceToLambdaTransformer.INSTANCE.transform(block);
      LambdaToAnonymousTransformer.INSTANCE.transform(block);
      ToObjectInheritorTransformer.INSTANCE.transform(block);
      return block.getText();
    });

    final String result = RESULT_VARIABLE_DECLARATION + LINE_SEPARATOR +
                          expression + LINE_SEPARATOR +
                          RESULT_VARIABLE_NAME + ";";
    LOG.info("stream expression to trace:" + LINE_SEPARATOR + result);
    return result;
  }

  @NotNull
  private static StreamChain buildTraceChain(@NotNull StreamChain chain,
                                             @NotNull List<IntermediateCallHandler> intermediateCallHandlers,
                                             @NotNull TerminatorCallHandler terminatorHandler) {
    final List<IntermediateStreamCall> newIntermediateCalls = new ArrayList<>();

    final QualifierExpression qualifierExpression = chain.getQualifierExpression();
    newIntermediateCalls.add(createTimePeekCall(qualifierExpression.getTypeAfter()));

    final List<IntermediateStreamCall> intermediateCalls = chain.getIntermediateCalls();
    assert intermediateCalls.size() == intermediateCallHandlers.size();

    for (int i = 0, callCount = intermediateCallHandlers.size(); i < callCount; i++) {
      final IntermediateStreamCall call = intermediateCalls.get(i);
      final IntermediateCallHandler handler = intermediateCallHandlers.get(i);

      newIntermediateCalls.addAll(handler.additionalCallsBefore());

      newIntermediateCalls.add(handler.transformCall(call));
      newIntermediateCalls.add(createTimePeekCall(call.getTypeAfter()));

      newIntermediateCalls.addAll(handler.additionalCallsAfter());
    }

    newIntermediateCalls.addAll(terminatorHandler.additionalCallsBefore());
    final TerminatorStreamCall terminatorCall = chain.getTerminationCall();

    return new StreamChainImpl(qualifierExpression, newIntermediateCalls,
                               terminatorHandler.transformCall(terminatorCall), chain.getContext());
  }

  @NotNull
  private static IntermediateStreamCall createTimePeekCall(@NotNull GenericType elementType) {
    return new PeekCall("x -> time.incrementAndGet()", elementType);
  }

  private static String buildDeclarations(@NotNull List<IntermediateCallHandler> intermediateCallsHandlers,
                                          @NotNull TerminatorCallHandler terminatorHandler) {
    final StringBuilder builder = new StringBuilder();
    builder.append("final long startTime = java.lang.System.nanoTime();" + LINE_SEPARATOR);
    final int resultArraySize = 2 + intermediateCallsHandlers.size();
    builder.append(String.format("final java.lang.Object[] info = new java.lang.Object[%d];" + LINE_SEPARATOR, resultArraySize))
      .append("final java.util.concurrent.atomic.AtomicInteger time = new java.util.concurrent.atomic.AtomicInteger(0);")
      .append(LINE_SEPARATOR);

    intermediateCallsHandlers.forEach(x -> builder.append(x.additionalVariablesDeclaration()));
    builder.append(terminatorHandler.additionalVariablesDeclaration());

    return builder.toString();
  }

  @NotNull
  private static String buildStreamExpression(@NotNull StreamChain chain) {
    final GenericType resultType = chain.getTerminationCall().getResultType();

    final String resultExpression;
    final String additionalDeclarations;
    final String additionalEvaluation;
    if (resultType.equals(JavaTypes.INSTANCE.getVoidType())) {
      additionalDeclarations = "";
      additionalEvaluation = chain.getText() + ";" + LINE_SEPARATOR;
      resultExpression = "new Object[1]";
    }
    else {
      final String resultArrayType = resultType.getVariableTypeName() + "[]";
      additionalDeclarations = resultArrayType + " evaluationResult = new " + resultType.getVariableTypeName() + "[] {" + LINE_SEPARATOR +
                               resultType.getDefaultValue() + LINE_SEPARATOR +
                               "};" + LINE_SEPARATOR;
      additionalEvaluation = "evaluationResult = new " + resultArrayType + " {" + chain.getText() + "};" + LINE_SEPARATOR;
      resultExpression = "evaluationResult";
    }

    return "Object streamResult = null;" + LINE_SEPARATOR +
           additionalDeclarations +
           "try {" + LINE_SEPARATOR +
           additionalEvaluation +
           "  streamResult = " + resultExpression + ";" + LINE_SEPARATOR +
           "}" + LINE_SEPARATOR +
           "catch(Throwable t) {" + LINE_SEPARATOR +
           "  streamResult = new Throwable[]{t};" + LINE_SEPARATOR +
           "}" + LINE_SEPARATOR;
  }

  @NotNull
  private static String buildFillInfo(List<IntermediateCallHandler> intermediateCallsHandlers,
                                      TerminatorCallHandler terminatorHandler) {
    final StringBuilder builder = new StringBuilder();

    final Iterator<TraceHandler> iterator =
      StreamEx.of(intermediateCallsHandlers).map(x -> (TraceHandler)x).append(terminatorHandler).iterator();

    int i = 0;
    while (iterator.hasNext()) {
      final TraceHandler handler = iterator.next();
      builder.append("{").append(LINE_SEPARATOR);
      builder.append(handler.prepareResult());
      builder.append(String.format("info[%d] = %s;", i, handler.getResultExpression())).append(LINE_SEPARATOR);
      builder.append("}").append(LINE_SEPARATOR);
      i++;
    }

    builder.append("final long[] elapsedTime = new long[]{ java.lang.System.nanoTime() - startTime };" + LINE_SEPARATOR);
    builder.append(RESULT_EXPRESSION);

    return builder.toString();
  }

  @NotNull
  private static List<IntermediateCallHandler> getHandlers(@NotNull LibraryManager libraryManager,
                                                           @NotNull List<IntermediateStreamCall> intermediateCalls) {
    final List<IntermediateCallHandler> result = new ArrayList<>();

    int i = 1;
    for (final IntermediateStreamCall call : intermediateCalls) {
      result.add(libraryManager.getLibrary(call).createHandlerFactory(new DslImpl(new JavaStatementFactory())).getForIntermediate(i, call));
      i++;
    }

    return result;
  }
}
