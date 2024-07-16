// WITH_DEFAULT_VALUE: false

interface Repo {
    fun getById(int: Int) : String
}

class Foo(val repo: Repo){
    fun foo(){
        val value = <selection>(repo::getById)</selection>(1)
    }
}