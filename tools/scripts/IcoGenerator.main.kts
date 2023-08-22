// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:DependsOn("com.github.weisj:jsvg:1.0.0")
@file:DependsOn("net.ifok.image:image4j:0.7.2")

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.attributes.ViewBox
import com.github.weisj.jsvg.parser.SVGLoader
import net.ifok.image.image4j.codec.ico.ICOEncoder
import java.awt.image.BufferedImage
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.system.exitProcess

if (args.size != 3) {
  println("""
    The script generates a product .ico file from a pair of SVG images.
    usage: IcoGenerator /path/to/icon.svg /path/to/icon_16.svg /path/to/icon.ico
    """.trimIndent())
  exitProcess(1)
}

val svg = load(args[0])
val svg16 = load(args[1])

val renders = listOf(
  render(svg, 256),
  render(svg, 64),
  render(svg, 48),
  render(svg, 40),
  render(svg, 32),
  render(svg16, 24),
  render(svg16, 20),
  render(svg16, 16),
)

val ico = Path(args[2])
ico.parent.createDirectories()
ico.outputStream().use { ICOEncoder.write(renders, it) }

fun load(path: String): SVGDocument =
  Path(path).inputStream().use { SVGLoader().load(it) } ?: throw IllegalArgumentException("Cannot load ${path}")

fun render(svg: SVGDocument, size: Int): BufferedImage {
  val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
  val g = image.createGraphics()
  svg.render(null, g, ViewBox(0f, 0f, size.toFloat(), size.toFloat()))
  g.dispose()
  return image
}
