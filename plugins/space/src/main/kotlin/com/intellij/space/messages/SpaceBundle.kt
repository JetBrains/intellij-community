package com.intellij.space.messages

import com.intellij.BundleBase
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.*

object SpaceBundle {
  private const val BUNDLE = "space.messages.SpaceBundle"

  private var bundleReference: Reference<ResourceBundle>? = null

  private val bundle: ResourceBundle
    get() = bundleReference?.get() ?: run {
      val b = ResourceBundle.getBundle(BUNDLE)

      bundleReference = SoftReference(b)

      b
    }

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg parameters: Any): String =
    BundleBase.message(bundle, key, *parameters)
}
