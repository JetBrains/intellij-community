/**
 * @author cdr
 */
package com.intellij.psi.controlFlow;

public class ControlFlowInstructionVisitor {
  public void visitInstruction(Instruction instruction, int offset, int nextOffset) {

  }
  public void visitEmptyInstruction(EmptyInstruction instruction, int offset, int nextOffset) {
    visitSimpleInstruction(instruction, offset, nextOffset);
  }
  public void visitCommentInstruction(CommentInstruction instruction, int offset, int nextOffset) {
    visitSimpleInstruction(instruction, offset, nextOffset);
  }
  public void visitReadVariableInstruction(ReadVariableInstruction instruction, int offset, int nextOffset) {
    visitSimpleInstruction(instruction, offset, nextOffset);
  }
  public void visitWriteVariableInstruction(WriteVariableInstruction instruction, int offset, int nextOffset) {
    visitSimpleInstruction(instruction, offset, nextOffset);
  }
  public void visitSimpleInstruction(SimpleInstruction instruction, int offset, int nextOffset) {
    visitInstruction(instruction, offset, nextOffset);
  }
  public void visitBranchingInstruction(BranchingInstruction instruction, int offset, int nextOffset) {
    visitInstruction(instruction, offset, nextOffset);
  }
  public void visitConditionalBranchingInstruction(ConditionalBranchingInstruction instruction, int offset, int nextOffset) {
    visitBranchingInstruction(instruction, offset, nextOffset);
  }
  public void visitConditionalGoToInstruction(ConditionalGoToInstruction instruction, int offset, int nextOffset) {
    visitConditionalBranchingInstruction(instruction, offset, nextOffset);
  }
  public void visitConditionalThrowToInstruction(ConditionalThrowToInstruction instruction, int offset, int nextOffset) {
    visitConditionalBranchingInstruction(instruction, offset, nextOffset);
  }
  public void visitThrowToInstruction(ThrowToInstruction instruction, int offset, int nextOffset) {
    visitBranchingInstruction(instruction, offset, nextOffset);
  }
  public void visitGoToInstruction(GoToInstruction instruction, int offset, int nextOffset) {
    visitBranchingInstruction(instruction, offset, nextOffset);
  }
  public void visitCallInstruction(CallInstruction instruction, int offset, int nextOffset) {
    visitGoToInstruction(instruction, offset, nextOffset);
  }
  public void visitReturnInstruction(ReturnInstruction instruction, int offset, int nextOffset) {
    visitGoToInstruction(instruction, offset, nextOffset);
  }
}