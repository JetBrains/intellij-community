from dt_decorator import my_dt_decorator


@my_dt_decorator(kw_only=True)
class RecordViaDecorator:
    id: int
    name: str
