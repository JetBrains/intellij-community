def func():
    x = True
    class C:
        def me<caret>thod(self):
            nonlocal x
            x = False
