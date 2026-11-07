// ERROR: 'getName' overrides nothing.
// ERROR: 'name' hides member of supertype 'IUser' and needs an 'override' modifier.
package test

class UserImpl(private val name: String?) : IUser {
    override fun getName(): String? {
        return name
    }
}
