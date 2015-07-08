package org.jetbrains.settingsRepository

import com.intellij.CommonBundle
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.ResourceBundle
import kotlin.platform.platformStatic

class IcsBundle {
  companion object {
    private var ourBundle: Reference<ResourceBundle>? = null

    val BUNDLE: String = "messages.IcsBundle"

    platformStatic
    fun message(PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): String {
      return CommonBundle.message(getBundle(), key, *params)
    }

    private fun getBundle(): ResourceBundle {
      var bundle: ResourceBundle? = null
      if (ourBundle != null) {
        bundle = ourBundle!!.get()
      }
      if (bundle == null) {
        bundle = ResourceBundle.getBundle(BUNDLE)
        ourBundle = SoftReference(bundle)
      }
      return bundle!!
    }
  }
}