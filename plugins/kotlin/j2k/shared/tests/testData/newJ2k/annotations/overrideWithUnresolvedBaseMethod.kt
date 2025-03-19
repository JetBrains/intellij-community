// ERROR: Unresolved reference 'CustomFragment'.
// ERROR: Unresolved reference 'onFragmentCreate'.
// ERROR: 'onFragmentCreate' overrides nothing.
// ERROR: Unresolved reference 'CustomFragment'.
// ERROR: Unresolved reference 'androidx'.
import androidx.fragment.app.CustomFragment

class Test : CustomFragment() {
    public override fun onFragmentCreate(savedInstanceState: String?) {
        super.onFragmentCreate(savedInstanceState)
    }
}
