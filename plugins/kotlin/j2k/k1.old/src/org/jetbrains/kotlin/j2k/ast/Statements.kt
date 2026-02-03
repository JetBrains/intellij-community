// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.append

@K1Deprecation
abstract class Statement : Element() {
    object Empty : Statement() {
        override fun generateCode(builder: CodeBuilder) { }
        override val isEmpty: Boolean get() = true
    }
}

@K1Deprecation
class DeclarationStatement(val elements: List<Element>) : Statement() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append(elements, "\n")
    }
}

@K1Deprecation
class ExpressionListStatement(val expressions: List<Expression>) : Expression() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append(expressions, "\n")
    }
}

@K1Deprecation
class LabeledStatement(val name: Identifier, val statement: Element) : Statement() {
    override fun generateCode(builder: CodeBuilder) {
        builder append name append "@" append " " append statement
    }
}

@K1Deprecation
class ReturnStatement(val expression: Expression, val label: Identifier? = null) : Statement() {
    override fun generateCode(builder: CodeBuilder) {
        builder append "return"
        if (label != null) {
            builder append "@" append label
        }
        builder append " " append expression
    }
}

@K1Deprecation
class IfStatement(
  val condition: Expression,
  private val thenStatement: Element,
  private val elseStatement: Element,
  singleLine: Boolean
) : Expression() {

    private val br = if (singleLine) " " else "\n"
    private val brAfterElse = if (singleLine || elseStatement is IfStatement) " " else "\n"

    override fun generateCode(builder: CodeBuilder) {
        builder append "if (" append condition append ")" append br append thenStatement.wrapToBlockIfRequired()
        if (!elseStatement.isEmpty) {
            builder append br append "else" append brAfterElse append elseStatement.wrapToBlockIfRequired()
        }
        else if (thenStatement.isEmpty) {
            builder append ";"
        }
    }
}

// Loops --------------------------------------------------------------------------------------------------

@K1Deprecation
class WhileStatement(val condition: Expression, val body: Element, singleLine: Boolean) : Statement() {
    private val br = if (singleLine) " " else "\n"

    override fun generateCode(builder: CodeBuilder) {
        builder append "while (" append condition append ")" append br append body.wrapToBlockIfRequired()
        if (body.isEmpty) {
            builder append ";"
        }
    }
}

@K1Deprecation
class DoWhileStatement(val condition: Expression, val body: Element, singleLine: Boolean) : Statement() {
    private val br = if (singleLine) " " else "\n"

    override fun generateCode(builder: CodeBuilder) {
        builder append "do" append br append body.wrapToBlockIfRequired() append br append "while (" append condition append ")"
    }
}

@K1Deprecation
class ForeachStatement(
  private val variableName: Identifier,
  private val explicitVariableType: Type?,
  val collection: Expression,
  val body: Element,
  singleLine: Boolean
) : Statement() {

    private val br = if (singleLine) " " else "\n"

    override fun generateCode(builder: CodeBuilder) {
        builder append "for (" append variableName
        if (explicitVariableType != null) {
            builder append ":" append explicitVariableType
        }
        builder append " in " append collection append ")" append br append body.wrapToBlockIfRequired()
        if (body.isEmpty) {
            builder append ";"
        }
    }
}

@K1Deprecation
class BreakStatement(val label: Identifier = Identifier.Empty) : Statement() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append("break").appendWithPrefix(label, "@")
    }
}

@K1Deprecation
class ContinueStatement(val label: Identifier = Identifier.Empty) : Statement() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append("continue").appendWithPrefix(label, "@")
    }
}

// Exceptions ----------------------------------------------------------------------------------------------

@K1Deprecation
class TryStatement(val block: Block, private val catches: List<CatchStatement>, val finallyBlock: Block) : Statement() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append("try\n").append(block).append("\n").append(catches, "\n").append("\n")
        if (!finallyBlock.isEmpty) {
            builder append "finally\n" append finallyBlock
        }
    }
}

@K1Deprecation
class ThrowStatement(val expression: Expression) : Expression() {
    override fun generateCode(builder: CodeBuilder) {
        builder append "throw " append expression
    }
}

@K1Deprecation
class CatchStatement(val variable: FunctionParameter, val block: Block) : Statement() {
    override fun generateCode(builder: CodeBuilder) {
        builder append "catch (" append variable append ") " append block
    }
}

// when --------------------------------------------------------------------------------------------------

@K1Deprecation
class WhenStatement(val subject: Expression, private val caseContainers: List<WhenEntry>) : Statement() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append("when (").append(subject).append(") {\n").append(caseContainers, "\n").append("\n}")
    }
}

@K1Deprecation
class WhenEntry(private val selectors: List<WhenEntrySelector>, val body: Statement) : Statement() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append(selectors, ", ").append(" -> ").append(body)
    }
}

@K1Deprecation
abstract class WhenEntrySelector : Statement()

@K1Deprecation
class ValueWhenEntrySelector(val expression: Expression) : WhenEntrySelector() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append(expression)
    }
}

@K1Deprecation
class ElseWhenEntrySelector : WhenEntrySelector() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append("else")
    }
}

// Other ------------------------------------------------------------------------------------------------------

@K1Deprecation
class SynchronizedStatement(val expression: Expression, val block: Block) : Statement() {
    override fun generateCode(builder: CodeBuilder) {
        builder append "synchronized (" append expression append ") " append block
    }
}
