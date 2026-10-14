package sub

import base.Base
import helper.Helper

class <caret>Child : Base() {
    // INFO: {"checked": "true"}
    fun use(value: Helper): Helper = value
}
