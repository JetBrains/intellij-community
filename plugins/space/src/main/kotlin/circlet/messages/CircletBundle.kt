package circlet.messages

import com.intellij.*
import org.jetbrains.annotations.*
import java.lang.ref.*
import java.util.*

object CircletBundle {
    private const val BUNDLE = "circlet.messages.CircletBundle"

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
