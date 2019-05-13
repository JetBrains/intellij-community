def func():
    def lo<caret>cal():
        x = True
        def nested():
            nonlocal x
            x = False