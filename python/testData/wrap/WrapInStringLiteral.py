class Test:
    def execQueries(self, queries):
        pass

    def someFunction(self):
       self.execQueries("SELECT field1, field2, field3 FROM %s WHERE 1=1<caret>")