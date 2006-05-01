/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.fileEditor.impl;

import com.intellij.util.ui.UIUtil;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;

/**
 * @author max
 */
public class WaverGraphicsDecorator extends Graphics2D {
  public static int WAVE_ALPHA_KEY = 0xFE;
  private Graphics2D myOriginal;
  private Color myWaveColor;

  public WaverGraphicsDecorator(final Graphics2D original, final Color waveColor) {
    myOriginal = original;
    myWaveColor = waveColor;
  }

  private void drawWave(String text, int offset, int baseline) {
    Color fore = getColor();
    if (fore.getAlpha() == WAVE_ALPHA_KEY) {
      int width = getFontMetrics().stringWidth(text);
      setColor(myWaveColor);
      final int wavedAt = baseline + 1;
      for (int x = offset; x < offset + width; x += 4) {
        UIUtil.drawLine(this, x, wavedAt, x + 2, wavedAt + 2);
        UIUtil.drawLine(this, x + 3, wavedAt + 1, x + 4, wavedAt);
      }
      setColor(fore);
    }
  }

  public void draw(final Shape s) {
    myOriginal.draw(s);
  }

  public boolean drawImage(final Image img, final AffineTransform xform, final ImageObserver obs) {
    return myOriginal.drawImage(img, xform, obs);
  }

  public void drawImage(final BufferedImage img, final BufferedImageOp op, final int x, final int y) {
    myOriginal.drawImage(img, op, x, y);
  }

  public void drawRenderedImage(final RenderedImage img, final AffineTransform xform) {
    myOriginal.drawRenderedImage(img, xform);
  }

  public void drawRenderableImage(final RenderableImage img, final AffineTransform xform) {
    myOriginal.drawRenderableImage(img, xform);
  }

  public void drawString(final String str, final int x, final int y) {
    myOriginal.drawString(str, x, y);
    drawWave(str, x, y);
  }

  public void drawString(final String s, final float x, final float y) {
    myOriginal.drawString(s, x, y);
    drawWave(s, (int)x, (int)y);
  }

  public void drawString(final AttributedCharacterIterator iterator, final int x, final int y) {
    myOriginal.drawString(iterator, x, y);
    //TODO: drawWave
  }

  public void drawString(final AttributedCharacterIterator iterator, final float x, final float y) {
    myOriginal.drawString(iterator, x, y);
    //TODO: drawWave
  }

  public void drawGlyphVector(final GlyphVector g, final float x, final float y) {
    myOriginal.drawGlyphVector(g, x, y);
    //TODO: drawWave
  }

  public void fill(final Shape s) {
    myOriginal.fill(s);
  }

  public boolean hit(final Rectangle rect, final Shape s, final boolean onStroke) {
    return myOriginal.hit(rect, s, onStroke);
  }

  public GraphicsConfiguration getDeviceConfiguration() {
    return myOriginal.getDeviceConfiguration();
  }

  public void setComposite(final Composite comp) {
    myOriginal.setComposite(comp);
  }

  public void setPaint(final Paint paint) {
    myOriginal.setPaint(paint);
  }

  public void setStroke(final Stroke s) {
    myOriginal.setStroke(s);
  }

  public void setRenderingHint(final RenderingHints.Key hintKey, final Object hintValue) {
    myOriginal.setRenderingHint(hintKey, hintValue);
  }

  public Object getRenderingHint(final RenderingHints.Key hintKey) {
    return myOriginal.getRenderingHint(hintKey);
  }

  public void setRenderingHints(final Map<?, ?> hints) {
    myOriginal.setRenderingHints(hints);
  }

  public void addRenderingHints(final Map<?, ?> hints) {
    myOriginal.addRenderingHints(hints);
  }

  public RenderingHints getRenderingHints() {
    return myOriginal.getRenderingHints();
  }

  public void translate(final int x, final int y) {
    myOriginal.translate(x, y);
  }

  public void translate(final double tx, final double ty) {
    myOriginal.translate(tx, ty);
  }

  public void rotate(final double theta) {
    myOriginal.rotate(theta);
  }

  public void rotate(final double theta, final double x, final double y) {
    myOriginal.rotate(theta, x, y);
  }

  public void scale(final double sx, final double sy) {
    myOriginal.scale(sx, sy);
  }

  public void shear(final double shx, final double shy) {
    myOriginal.shear(shx, shy);
  }

  public void transform(final AffineTransform Tx) {
    myOriginal.transform(Tx);
  }

  public void setTransform(final AffineTransform Tx) {
    myOriginal.setTransform(Tx);
  }

  public AffineTransform getTransform() {
    return myOriginal.getTransform();
  }

  public Paint getPaint() {
    return myOriginal.getPaint();
  }

  public Composite getComposite() {
    return myOriginal.getComposite();
  }

  public void setBackground(final Color color) {
    myOriginal.setBackground(color);
  }

  public Color getBackground() {
    return myOriginal.getBackground();
  }

  public Stroke getStroke() {
    return myOriginal.getStroke();
  }

  public void clip(final Shape s) {
    myOriginal.clip(s);
  }

  public FontRenderContext getFontRenderContext() {
    return myOriginal.getFontRenderContext();
  }

  public Graphics create() {
    return new WaverGraphicsDecorator((Graphics2D)myOriginal.create(), myWaveColor);
  }

  public Color getColor() {
    return myOriginal.getColor();
  }

  public void setColor(final Color c) {
    myOriginal.setColor(c);
  }

  public void setPaintMode() {
    myOriginal.setPaintMode();
  }

  public void setXORMode(final Color c1) {
    myOriginal.setXORMode(c1);
  }

  public Font getFont() {
    return myOriginal.getFont();
  }

  public void setFont(final Font font) {
    myOriginal.setFont(font);
  }

  public FontMetrics getFontMetrics(final Font f) {
    return myOriginal.getFontMetrics(f);
  }

  public Rectangle getClipBounds() {
    return myOriginal.getClipBounds();
  }

  public void clipRect(final int x, final int y, final int width, final int height) {
    myOriginal.clipRect(x, y, width, height);
  }

  public void setClip(final int x, final int y, final int width, final int height) {
    myOriginal.setClip(x, y, width, height);
  }

  public Shape getClip() {
    return myOriginal.getClip();
  }

  public void setClip(final Shape clip) {
    myOriginal.setClip(clip);
  }

  public void copyArea(final int x, final int y, final int width, final int height, final int dx, final int dy) {
    myOriginal.copyArea(x, y, width, height, dx, dy);
  }

  public void drawLine(final int x1, final int y1, final int x2, final int y2) {
    myOriginal.drawLine(x1, y1, x2, y2);
  }

  public void fillRect(final int x, final int y, final int width, final int height) {
    myOriginal.fillRect(x, y, width, height);
  }

  public void clearRect(final int x, final int y, final int width, final int height) {
    myOriginal.clearRect(x, y, width, height);
  }

  public void drawRoundRect(final int x, final int y, final int width, final int height, final int arcWidth, final int arcHeight) {
    myOriginal.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
  }

  public void fillRoundRect(final int x, final int y, final int width, final int height, final int arcWidth, final int arcHeight) {
    myOriginal.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
  }

  public void drawOval(final int x, final int y, final int width, final int height) {
    myOriginal.drawOval(x, y, width, height);
  }

  public void fillOval(final int x, final int y, final int width, final int height) {
    myOriginal.fillOval(x, y, width, height);
  }

  public void drawArc(final int x, final int y, final int width, final int height, final int startAngle, final int arcAngle) {
    myOriginal.drawArc(x, y, width, height, startAngle, arcAngle);
  }

  public void fillArc(final int x, final int y, final int width, final int height, final int startAngle, final int arcAngle) {
    myOriginal.fillArc(x, y, width, height, startAngle, arcAngle);
  }

  public void drawPolyline(final int[] xPoints, final int[] yPoints, final int nPoints) {
    myOriginal.drawPolyline(xPoints, yPoints, nPoints);
  }

  public void drawPolygon(final int[] xPoints, final int[] yPoints, final int nPoints) {
    myOriginal.drawPolygon(xPoints, yPoints, nPoints);
  }

  public void fillPolygon(final int[] xPoints, final int[] yPoints, final int nPoints) {
    myOriginal.fillPolygon(xPoints, yPoints, nPoints);
  }

  public boolean drawImage(final Image img, final int x, final int y, final ImageObserver observer) {
    return myOriginal.drawImage(img, x, y, observer);
  }

  public boolean drawImage(final Image img, final int x, final int y, final int width, final int height, final ImageObserver observer) {
    return myOriginal.drawImage(img, x, y, width, height, observer);
  }

  public boolean drawImage(final Image img, final int x, final int y, final Color bgcolor, final ImageObserver observer) {
    return myOriginal.drawImage(img, x, y, bgcolor, observer);
  }

  public boolean drawImage(final Image img,
                           final int x,
                           final int y,
                           final int width,
                           final int height,
                           final Color bgcolor,
                           final ImageObserver observer) {
    return myOriginal.drawImage(img, x, y, width, height, bgcolor, observer);
  }

  public boolean drawImage(final Image img,
                           final int dx1,
                           final int dy1,
                           final int dx2,
                           final int dy2,
                           final int sx1,
                           final int sy1,
                           final int sx2,
                           final int sy2,
                           final ImageObserver observer) {
    return myOriginal.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
  }

  public boolean drawImage(final Image img,
                           final int dx1,
                           final int dy1,
                           final int dx2,
                           final int dy2,
                           final int sx1,
                           final int sy1,
                           final int sx2,
                           final int sy2,
                           final Color bgcolor,
                           final ImageObserver observer) {
    return myOriginal.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer);
  }

  public void dispose() {
    //myOriginal.dispose();
  }


  public Rectangle getClipRect() {
    return myOriginal.getClipRect();
  }

  public boolean hitClip(final int x, final int y, final int width, final int height) {
    return myOriginal.hitClip(x, y, width, height);
  }

  public Rectangle getClipBounds(final Rectangle r) {
    return myOriginal.getClipBounds(r);
  }

  public void fill3DRect(final int x, final int y, final int width, final int height, final boolean raised) {
    myOriginal.fill3DRect(x, y, width, height, raised);
  }

  public void draw3DRect(final int x, final int y, final int width, final int height, final boolean raised) {
    myOriginal.draw3DRect(x, y, width, height, raised);
  }

  public Graphics create(final int x, final int y, final int width, final int height) {
    return myOriginal.create(x, y, width, height);
  }

  public void drawRect(final int x, final int y, final int width, final int height) {
    myOriginal.drawRect(x, y, width, height);
  }

  public void drawPolygon(final Polygon p) {
    myOriginal.drawPolygon(p);
  }

  public void fillPolygon(final Polygon p) {
    myOriginal.fillPolygon(p);
  }

  public FontMetrics getFontMetrics() {
    return myOriginal.getFontMetrics();
  }
}
