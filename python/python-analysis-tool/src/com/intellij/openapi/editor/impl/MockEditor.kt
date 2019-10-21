package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Insets
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.geom.Point2D
import javax.swing.JComponent
import javax.swing.border.Border

class MockEditor(private val myFile: VirtualFile): Editor {
  private val myCaretModel = MockCaretModel()

  override fun getDocument(): Document = FileDocumentManager.getInstance().getDocument(myFile)!!

  override fun isViewer(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getComponent(): JComponent {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getContentComponent(): JComponent {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun setBorder(border: Border?) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getInsets(): Insets {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getSelectionModel(): SelectionModel {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getMarkupModel(): MarkupModel {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getFoldingModel(): FoldingModel {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getScrollingModel(): ScrollingModel {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getCaretModel(): CaretModel = myCaretModel

  override fun getSoftWrapModel(): SoftWrapModel {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getSettings(): EditorSettings {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getColorsScheme(): EditorColorsScheme {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getLineHeight(): Int {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun logicalPositionToXY(pos: LogicalPosition): Point {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun logicalPositionToOffset(pos: LogicalPosition): Int {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun logicalToVisualPosition(logicalPos: LogicalPosition): VisualPosition {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun visualPositionToXY(visible: VisualPosition): Point {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun visualPositionToPoint2D(pos: VisualPosition): Point2D {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun visualToLogicalPosition(visiblePos: VisualPosition): LogicalPosition {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun offsetToLogicalPosition(offset: Int): LogicalPosition {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun offsetToVisualPosition(offset: Int): VisualPosition {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun offsetToVisualPosition(offset: Int, leanForward: Boolean, beforeSoftWrap: Boolean): VisualPosition {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun xyToLogicalPosition(p: Point): LogicalPosition {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun xyToVisualPosition(p: Point): VisualPosition {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun xyToVisualPosition(p: Point2D): VisualPosition {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun addEditorMouseListener(listener: EditorMouseListener) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun removeEditorMouseListener(listener: EditorMouseListener) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun addEditorMouseMotionListener(listener: EditorMouseMotionListener) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun removeEditorMouseMotionListener(listener: EditorMouseMotionListener) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isDisposed(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getProject(): Project? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isInsertMode(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isColumnMode(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun isOneLineMode(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getGutter(): EditorGutter {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getMouseEventArea(e: MouseEvent): EditorMouseEventArea? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun setHeaderComponent(header: JComponent?) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun hasHeaderComponent(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getHeaderComponent(): JComponent? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getIndentsModel(): IndentsModel {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getInlayModel(): InlayModel {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getEditorKind(): EditorKind {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun <T : Any?> getUserData(key: Key<T>): T? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}