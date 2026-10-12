package sub

import base.Base

class Payload

typealias Alias = Payload

class <caret>Child : Base() {
    // INFO: {"checked": "true"}
    fun use(value: Alias): Alias = value
}
