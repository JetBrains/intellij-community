def outer():
    s = "aba"

    def inner():
        global s
        #      <ref>