// ERROR: NOTHING_TO_OVERRIDE
// ERROR: VIRTUAL_MEMBER_HIDDEN
package test

class UserImpl(private val name: String?) : IUser {
    override fun getName(): String? {
        return name
    }
}
