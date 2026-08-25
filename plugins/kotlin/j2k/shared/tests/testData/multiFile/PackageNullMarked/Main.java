package test;

public class Main {
    Person use(Repo<Person> repo, Person p) {
        return repo.save(p);
    }
}
