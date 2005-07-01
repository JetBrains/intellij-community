/**
 * @author Alexey
 */
package com.intellij.psi.controlFlow;

class CompositeInstructionClientVisitor extends InstructionClientVisitor<Object[]> {
  private final InstructionClientVisitor[] myVisitors;

  public CompositeInstructionClientVisitor(InstructionClientVisitor[] visitors) {
    myVisitors = visitors;
  }

  public Object[] getResult() {
    Object[] result = new Object[myVisitors.length];
    for (int i = 0; i < myVisitors.length; i++) {
      final InstructionClientVisitor visitor = myVisitors[i];
      result[i] = visitor.getResult();
    }
    return result;
  }

  public void visitInstruction(Instruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitInstruction(instruction, offset, nextOffset);
    }
  }

  public void visitEmptyInstruction(EmptyInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitEmptyInstruction(instruction, offset, nextOffset);
    }
  }

  public void visitCommentInstruction(CommentInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitCommentInstruction(instruction, offset, nextOffset);
    }
  }

  public void visitReadVariableInstruction(ReadVariableInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitReadVariableInstruction(instruction, offset, nextOffset);
    }
  }

  public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitWriteVariableInstruction(instruction, offset, nextOffset);
    }
  }

  public void visitSimpleInstruction(SimpleInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitSimpleInstruction(instruction, offset, nextOffset);
    }
  }

  public void visitBranchingInstruction(BranchingInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitBranchingInstruction(instruction, offset, nextOffset);
    }
  }

  public void visitConditionalBranchingInstruction(ConditionalBranchingInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitConditionalBranchingInstruction(instruction, offset, nextOffset);
    }
  }

  public void visitConditionalGoToInstruction(ConditionalGoToInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitConditionalGoToInstruction(instruction, offset, nextOffset);
    }
  }

  public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitConditionalThrowToInstruction(instruction, offset, nextOffset);
    }
  }

  public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitThrowToInstruction(instruction, offset, nextOffset);
    }
  }

  public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitGoToInstruction(instruction, offset, nextOffset);
    }
  }

  public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitCallInstruction(instruction, offset, nextOffset);
    }
  }

  public void visitReturnInstruction(ReturnInstruction instruction, int offset, int nextOffset) {
    for (final InstructionClientVisitor visitor : myVisitors) {
      visitor.visitReturnInstruction(instruction, offset, nextOffset);
    }
  }
}