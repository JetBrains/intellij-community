// ERROR: Unresolved reference: androidx
// ERROR: Unresolved reference: CustomFragment
// ERROR: 'onFragmentCreate' overrides nothing
// ERROR: Unresolved reference: onFragmentCreate
import androidx.fragment.app.CustomFragment

class Test : CustomFragment() {
    override fun onFragmentCreate(savedInstanceState: String?) {
        super.onFragmentCreate(savedInstanceState)
    }
}
