// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.performancePlugin.remotedriver.dataextractor

import java.awt.*
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ImageObserver
import java.awt.image.RenderedImage
import java.awt.image.renderable.RenderableImage

internal abstract class ExtractorGraphics2d(private val g: Graphics2D) : Graphics2D() {
  override fun getClipBounds(): Rectangle? {
    return g.clipBounds
  }

  override fun drawPolyline(xPoints: IntArray?, yPoints: IntArray?, nPoints: Int) {
    g.drawPolyline(xPoints, yPoints, nPoints)
  }

  override fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int) {
    g.drawLine(x1, y1, x2, y2)
  }

  override fun copyArea(x: Int, y: Int, width: Int, height: Int, dx: Int, dy: Int) {
    g.copyArea(x, y, width, height, dx, dy)
  }

  override fun draw(s: Shape?) {
    g.draw(s)
  }

  override fun setStroke(s: Stroke?) {
    g.stroke = s
  }

  override fun getComposite(): Composite? {
    return g.composite
  }

  override fun fillArc(x: Int, y: Int, width: Int, height: Int, startAngle: Int, arcAngle: Int) {
    g.fillArc(x, y, width, height, startAngle, arcAngle)
  }

  override fun fill(s: Shape?) {
    g.fill(s)
  }

  private val gc: GraphicsConfiguration by lazy {
    GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.configurations[0]
  }

  override fun getDeviceConfiguration(): GraphicsConfiguration? {
    return g.deviceConfiguration ?: gc
  }

  override fun getBackground(): Color? {
    return g.background
  }

  override fun clip(s: Shape?) {
    g.clip = s
  }

  override fun setPaint(paint: Paint?) {
    g.paint = paint
  }


  override fun clipRect(x: Int, y: Int, width: Int, height: Int) {
    g.clearRect(x, y, width, height)
  }

  override fun shear(shx: Double, shy: Double) {
    g.shear(shx, shy)
  }

  override fun transform(Tx: AffineTransform?) {
    g.transform(Tx)
  }

  override fun setPaintMode() {
    g.setPaintMode()
  }

  override fun getColor(): Color? {
    return g.color
  }

  override fun scale(sx: Double, sy: Double) {
    g.scale(sx, sy)
  }

  override fun getFontRenderContext(): FontRenderContext? {
    return g.fontRenderContext
  }

  override fun setXORMode(c1: Color?) {
    g.setXORMode(c1)
  }

  override fun addRenderingHints(hints: MutableMap<*, *>?) {
    g.addRenderingHints(hints)
  }

  override fun getRenderingHints(): RenderingHints {
    return g.renderingHints
  }

  override fun setFont(font: Font?) {
    g.font = font
  }

  override fun getFont(): Font? {
    return g.font
  }

  override fun getStroke(): Stroke? {
    return g.stroke
  }

  override fun fillOval(x: Int, y: Int, width: Int, height: Int) {
    g.fillOval(x, y, width, height)
  }

  override fun getClip(): Shape? {
    return g.clip
  }

  override fun drawRenderedImage(img: RenderedImage?, xform: AffineTransform?) {
    return g.drawRenderedImage(img, xform)
  }

  override fun dispose() {
    g.dispose()
  }

  override fun setClip(x: Int, y: Int, width: Int, height: Int) {
    g.setClip(x, y, width, height)
  }

  override fun setClip(clip: Shape?) {
    g.clip = clip
  }

  override fun setRenderingHints(hints: MutableMap<*, *>?) {
    g.setRenderingHints(hints)
  }

  override fun getTransform(): AffineTransform {
    return g.transform
  }

  override fun drawOval(x: Int, y: Int, width: Int, height: Int) {
    g.drawOval(x, y, width, height)
  }

  override fun drawRenderableImage(img: RenderableImage?, xform: AffineTransform?) {
    g.drawRenderableImage(img, xform)
  }

  override fun setComposite(comp: Composite?) {
    g.composite = comp
  }

  override fun clearRect(x: Int, y: Int, width: Int, height: Int) {
    g.clearRect(x, y, width, height)
  }

  override fun drawPolygon(xPoints: IntArray?, yPoints: IntArray?, nPoints: Int) {
    g.drawPolygon(xPoints, yPoints, nPoints)
  }

  override fun setTransform(Tx: AffineTransform?) {
    g.transform = Tx
  }

  override fun getPaint(): Paint {
    return g.paint
  }

  override fun fillRect(x: Int, y: Int, width: Int, height: Int) {
    g.fillRect(x, y, width, height)
  }

  override fun drawRoundRect(x: Int, y: Int, width: Int, height: Int, arcWidth: Int, arcHeight: Int) {
    g.drawRoundRect(x, y, width, height, arcWidth, arcHeight)
  }

  override fun getFontMetrics(f: Font?): FontMetrics {
    return g.getFontMetrics(f)
  }

  override fun fillPolygon(xPoints: IntArray?, yPoints: IntArray?, nPoints: Int) {
    g.fillPolygon(xPoints, yPoints, nPoints)
  }

  override fun setColor(c: Color?) {
    g.color = c
  }

  override fun setRenderingHint(hintKey: RenderingHints.Key?, hintValue: Any?) {
    g.setRenderingHint(hintKey, hintValue)
  }

  override fun fillRoundRect(x: Int, y: Int, width: Int, height: Int, arcWidth: Int, arcHeight: Int) {
    g.fillRoundRect(x, y, width, height, arcWidth, arcHeight)
  }

  override fun drawArc(x: Int, y: Int, width: Int, height: Int, startAngle: Int, arcAngle: Int) {
    g.drawArc(x, y, width, height, startAngle, arcAngle)
  }

  override fun getRenderingHint(hintKey: RenderingHints.Key?): Any? {
    return g.getRenderingHint(hintKey)
  }

  override fun hit(rect: Rectangle?, s: Shape?, onStroke: Boolean): Boolean {
    return g.hit(rect, s, onStroke)
  }

  override fun setBackground(color: Color?) {
    g.background = color
  }

  override fun drawImage(img: Image?, xform: AffineTransform?, obs: ImageObserver?): Boolean {
    return g.drawImage(img, xform, obs)
  }

  override fun drawImage(img: BufferedImage?, op: BufferedImageOp?, x: Int, y: Int) {
    g.drawImage(img, op, x, y)
  }

  override fun drawImage(img: Image?, x: Int, y: Int, observer: ImageObserver?): Boolean {
    return g.drawImage(img, x, y, observer)
  }

  override fun drawImage(img: Image?, x: Int, y: Int, width: Int, height: Int, observer: ImageObserver?): Boolean {
    return g.drawImage(img, x, y, width, height, observer)
  }

  override fun drawImage(img: Image?, x: Int, y: Int, bgcolor: Color?, observer: ImageObserver?): Boolean {
    return g.drawImage(img, x, y, bgcolor, observer)
  }

  override fun drawImage(
    img: Image?,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    bgcolor: Color?,
    observer: ImageObserver?
  ): Boolean {
    return g.drawImage(img, x, y, width, height, bgcolor, observer)
  }

  override fun drawImage(
    img: Image?,
    dx1: Int,
    dy1: Int,
    dx2: Int,
    dy2: Int,
    sx1: Int,
    sy1: Int,
    sx2: Int,
    sy2: Int,
    observer: ImageObserver?,
  ): Boolean {
    return g.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer)
  }

  override fun drawImage(
    img: Image?,
    dx1: Int,
    dy1: Int,
    dx2: Int,
    dy2: Int,
    sx1: Int,
    sy1: Int,
    sx2: Int,
    sy2: Int,
    bgcolor: Color?,
    observer: ImageObserver?
  ): Boolean {
    return g.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer)
  }

  protected fun getTextByGlyphVector(g: GlyphVector): String {
    return buildString {
      (0 until g.numGlyphs).forEach {
        CharByGlyphFinder.findCharByGlyph(g.font, g.fontRenderContext, g.getGlyphCode(it))?.let { char ->
          append(char)
        }
      }
    }
  }
}