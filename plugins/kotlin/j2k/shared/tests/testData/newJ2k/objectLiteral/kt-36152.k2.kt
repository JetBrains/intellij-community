// ERROR: 'onPreferenceClick' hides member of supertype 'OnPreferenceClickListener' and needs an 'override' modifier.
// ERROR: This type does not have a constructor.
import Preference.OnPreferenceClickListener

class Foo {
    fun foo(l: Preference, pm: Preference) {
        l.setOnPreferenceClickListener(OnPreferenceClickListener { p: Preference? -> true })

        pm.setOnPreferenceClickListener(object : OnPreferenceClickListener() {
            fun onPreferenceClick(preference: Preference?): Boolean {
                return true
            }
        })
    }

    private fun bar(l: Preference) {
        l.setOnPreferenceClickListener(OnPreferenceClickListener { p: Preference? -> true })
    }
}
