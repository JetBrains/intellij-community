from tensorflow._aliases import FloatTensorCompatible

def assert_greater_equal(
    x: FloatTensorCompatible,
    y: FloatTensorCompatible,
    message: str | None = None,
    summarize: int | None = None,
    name: str | None = None,
) -> None: ...
def __getattr__(name: str): ...  # incomplete module
