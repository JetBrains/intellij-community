annotation class DeprecatedForSdk(val message: String)

@DeprecatedForSdk("Use android.Manifest.permission.ACCESS_FINE_LOCATION instead")
const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"