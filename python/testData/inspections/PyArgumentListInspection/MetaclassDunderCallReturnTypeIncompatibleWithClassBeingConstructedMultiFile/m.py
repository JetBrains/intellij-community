class Meta(type):
    def call(cls, *args, **kwargs) -> object: ...

    __call__ = call
