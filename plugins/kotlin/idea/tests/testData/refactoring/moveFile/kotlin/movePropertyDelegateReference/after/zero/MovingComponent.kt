package zero

class MovingDelegate {
    operator fun getValue(_this: Any?, p: Any?) = 1
}

class MovingDelegateExtended

operator fun MovingDelegateExtended.getValue(_this: Any?, p: Any?) = 2