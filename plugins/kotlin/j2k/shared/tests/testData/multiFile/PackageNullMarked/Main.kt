package test

class Main {
    fun use(repo: Repo<Person>, p: Person): Person {
        return repo.save<Person>(p)
    }
}
