/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.controlflow;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public class InstructionBuilder {
  private InstructionBuilder() {
  }

  @NotNull
  public static List<Instruction> buildInstructions(@NotNull ControlFlowBuilder builder,
                                                    @NotNull List<PyTypeAssertionEvaluator.Assertion> assertions) {
    final List<Instruction> result = Lists.newArrayList();
    for (PyTypeAssertionEvaluator.Assertion def : assertions) {
      final PyReferenceExpression e = def.getElement();
      final QualifiedName qname = e.asQualifiedName();
      final String name = qname != null ? qname.toString() : e.getName();
      result.add(ReadWriteInstruction.assertType(builder, e, name, def.getTypeEvalFunction()));
    }
    return result;
  }

  @NotNull
  public static List<Instruction> addAssertInstructions(@NotNull ControlFlowBuilder builder,
                                                        @NotNull PyTypeAssertionEvaluator assertionEvaluator) {
    final List<Instruction> instructions = buildInstructions(builder, assertionEvaluator.getDefinitions());
    for (Instruction instr : instructions) {
      builder.addNode(instr);
    }
    return instructions;
  }
}
