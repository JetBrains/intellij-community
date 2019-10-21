package com.intellij.openapi.editor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.markup.TextAttributes

class MockCaretModel: CaretModel {
  private var myOffset = 0

  override fun moveCaretRelatively(columnShift: Int,
                                   lineShift: Int,
                                   withSelection: Boolean,
                                   blockSelection: Boolean,
                                   scrollToCaret: Boolean) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun moveToLogicalPosition(pos: LogicalPosition) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun moveToVisualPosition(pos: VisualPosition) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun moveToOffset(offset: Int) {
    myOffset = offset
  }

  override fun moveToOffset(offset: Int, locateBeforeSoftWrap: Boolean) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isUpToDate(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getLogicalPosition(): LogicalPosition {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getVisualPosition(): VisualPosition {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getOffset(): Int = myOffset

  override fun addCaretListener(listener: CaretListener) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun removeCaretListener(listener: CaretListener) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getVisualLineStart(): Int {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getVisualLineEnd(): Int {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getTextAttributes(): TextAttributes {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun supportsMultipleCarets(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getCurrentCaret(): Caret {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getPrimaryCaret(): Caret {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getCaretCount(): Int {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getAllCarets(): MutableList<Caret> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getCaretAt(pos: VisualPosition): Caret? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun addCaret(pos: VisualPosition): Caret? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun addCaret(pos: VisualPosition, makePrimary: Boolean): Caret? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun removeCaret(caret: Caret): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun removeSecondaryCarets() {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun setCaretsAndSelections(caretStates: MutableList<out CaretState>) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun setCaretsAndSelections(caretStates: MutableList<out CaretState>, updateSystemSelection: Boolean) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getCaretsAndSelections(): MutableList<CaretState> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun runForEachCaret(action: CaretAction) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun runForEachCaret(action: CaretAction, reverseOrder: Boolean) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun addCaretActionListener(listener: CaretActionListener, disposable: Disposable) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun runBatchCaretOperation(runnable: Runnable) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}