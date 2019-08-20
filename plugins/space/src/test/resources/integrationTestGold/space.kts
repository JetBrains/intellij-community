task("myTask") {
    this.fork {
        run("hello-world1")
        run("hello-world2")
    }
}
