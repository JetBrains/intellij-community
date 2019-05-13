def foo():
    def bar():
        def baz():
            if comments:
                for comment in comments:
                    record += '    <comment ' + "".join(
                        ca + '=' + quoteattr(comment[ca]) + ' ' for ca in comment) + '/>\n'
