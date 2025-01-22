#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import numpy as np
import io
import base64


try:
    import tensorflow as tf
except ImportError:
    pass
try:
    import torch
except ImportError:
    pass


DEFAULT_IMAGE_FORMAT = 'PNG'
DEFAULT_ENCODING = 'utf-8'
GRAYSCALE_MODE = 'L'
RGB_MODE = 'RGB'


def get_bytes(arr):
    # type: (np.ndarray) -> str
    try:
        from PIL import Image

        try:
            import tensorflow as tf
            if isinstance(arr, tf.SparseTensor):
                arr = tf.sparse.to_dense(tf.sparse.reorder(arr))
        except ImportError:
            pass

        arr = arr.numpy()

        if not (np.issubdtype(arr.dtype, np.floating) or np.issubdtype(arr.dtype, np.integer)):
            raise ValueError("Error: Only numeric array types are supported.")

        if arr.ndim == 1:
            arr = np.expand_dims(arr, axis=0)

        arr_min, arr_max = np.min(arr), np.max(arr)
        if arr_min == arr_max:  # handle constant values
            arr = np.full_like(arr, 127, dtype=np.uint8)
        else:
            arr = ((arr - arr_min) / (arr_max - arr_min) * 255).astype(np.uint8)

        mode = GRAYSCALE_MODE if arr.ndim == 2 else RGB_MODE
        bytes_buffer = io.BytesIO()
        image = Image.fromarray(arr, mode=mode)
        image.save(bytes_buffer, format=DEFAULT_IMAGE_FORMAT)
        return base64.b64encode(bytes_buffer.getvalue()).decode(DEFAULT_ENCODING)
    except ImportError:
        return "Error: Pillow library is not installed."
    except Exception as e:
        return "Error: {}".format(e)