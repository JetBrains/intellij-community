interface BaseInterface {
    // INFO: {"checked": "true"}
    class Level1 {
        class Level2 {
            class Level3<caret>Class
        }
    }
}

class Implementation : BaseInterface
