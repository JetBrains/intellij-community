def func():
    x = True
    def loc<caret>al():
        def nested():
            nonlocal x
            x = False
            
