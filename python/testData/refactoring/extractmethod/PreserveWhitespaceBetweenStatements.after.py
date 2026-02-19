class Foo:
    def bar(self):
        print('hello')

        <caret>self.extracted()

        return self

    def extracted(self):
        print('awesome')

        print('world')
