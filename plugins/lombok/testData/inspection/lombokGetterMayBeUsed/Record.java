// IDEA-370631
record Person(String name, int age) {
    String getInfo() {
        return "Person " + name + " is " + age + " years old";
    }

    public String getName() {
        return this.name;
    }

    public int getAge() {
        return this.age;
    }
}
