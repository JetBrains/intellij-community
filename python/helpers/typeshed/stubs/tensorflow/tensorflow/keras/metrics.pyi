from tensorflow import Tensor, _TensorCompatible

def binary_crossentropy(
    y_true: _TensorCompatible, y_pred: _TensorCompatible, from_logits: bool = False, label_smoothing: float = 0.0, axis: int = -1
) -> Tensor: ...
def categorical_crossentropy(
    y_true: _TensorCompatible, y_pred: _TensorCompatible, from_logits: bool = False, label_smoothing: float = 0.0, axis: int = -1
) -> Tensor: ...
