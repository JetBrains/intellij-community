internal class Stage {
    fun context(acceptor: Acceptor) {
        acceptor.acceptFace(object : Face {
            override fun subject(p: String?) {
                println(p)
            }
        })
        acceptor.setFace(object : Face {
            override fun subject(p: String?) {
                println(p)
            }
        })
    }
}
