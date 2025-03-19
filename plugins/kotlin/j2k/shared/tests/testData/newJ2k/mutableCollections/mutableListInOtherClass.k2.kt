class Owner {
    var list: MutableList<String?> = ArrayList<String?>()
}

class Updater {
    fun update(owner: Owner) {
        owner.list.add("")
    }
}
