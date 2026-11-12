class MarkDecorator:
    def __call__(self, *args: object, **kwargs: object) -> "MarkDecorator":
        return self


class MarkGenerator:
    skip: MarkDecorator

    def __getattr__(self, name: str) -> MarkDecorator:
        return MarkDecorator()


MARK_GEN: MarkGenerator = MarkGenerator()
