// ERROR: Unresolved reference 'CustomFragment'.
// ERROR: Unresolved reference 'CustomFragment'.
// ERROR: Unresolved reference 'androidx'.
// ERROR: Unresolved reference 'onFragmentCreate'.
import androidx.fragment.app.CustomFragment

class Test : CustomFragment() {
    public override fun onFragmentCreate(savedInstanceState: String?) {
        super.onFragmentCreate(savedInstanceState)
    }
}
