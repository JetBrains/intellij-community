class Base:
    def method(self, **kwargs):
        """
        :key foo: foo
        :key bar: bar
        :key baz:
        """


class Sub(Base):
    def met<the_ref>hod(self, *, foo, bar):
        super().method(foo=foo, bar=bar)
