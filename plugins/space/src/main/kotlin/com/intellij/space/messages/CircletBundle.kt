package com.intellij.space.messages

import com.intellij.BundleBase
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.*

object CircletBundle {
  private const val BUNDLE = "space.messages.CircletBundle"

  private var bundleReference: Reference<ResourceBundle>? = null

  private val bundle: ResourceBundle
    get() = bundleReference?.get() ?: run {
      val b = ResourceBundle.getBundle(BUNDLE)

      bundleReference = SoftReference(b)

      b
    }

  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg parameters: Any): String =
    BundleBase.message(bundle, key, *parameters)
}
