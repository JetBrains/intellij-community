import tensorflow as tf
from tensorflow._aliases import IntTensorCompatible, TensorCompatible

def random_crop(
    value: TensorCompatible, size: IntTensorCompatible, seed: int | None = None, name: str | None = None
) -> tf.Tensor: ...
def __getattr__(name: str): ...  # incomplete module
