job("myTask") {
    this.parallel {
        container("hello-world1")
        container("hello-world2")
    }
}
