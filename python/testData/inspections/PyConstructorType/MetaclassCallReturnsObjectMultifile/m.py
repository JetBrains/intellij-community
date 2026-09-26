class Meta(type):
    def call(cls) -> object: ...

    __call__ = call
