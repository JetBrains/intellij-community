package com.intellij.ide.starter.utils

fun Long.formatSize(): String {
  if (this < 1024) return "$this B"
  val z = (63 - java.lang.Long.numberOfLeadingZeros(this)) / 10
  return String.format("%.1f %sB", this.toDouble() / (1L shl z * 10), " KMGTPE"[z])
}

private const val BYTES_IN_MEGABYTE = 1024 * 1024

/**
 * Raw conversion of bytes to mb ()
 */
fun Long.bytesToMb(): Float = this.toFloat() / BYTES_IN_MEGABYTE
