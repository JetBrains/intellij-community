// ERROR: UNRESOLVED_IMPORT
// ERROR: UNRESOLVED_REFERENCE
// ERROR: UNRESOLVED_REFERENCE
// ERROR: UNRESOLVED_REFERENCE
import androidx.fragment.app.CustomFragment

class Test : CustomFragment() {
    public override fun onFragmentCreate(savedInstanceState: String?) {
        super.onFragmentCreate(savedInstanceState)
    }
}
