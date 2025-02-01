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

        arr_to_convert = arr

        try:
            import tensorflow as tf
            if isinstance(arr_to_convert, tf.SparseTensor):
                arr_to_convert = tf.sparse.to_dense(tf.sparse.reorder(arr_to_convert))
        except ImportError:
            pass

        arr_to_convert = arr_to_convert.numpy()

        if not (np.issubdtype(arr_to_convert.dtype, np.floating) or np.issubdtype(arr_to_convert.dtype, np.integer)):
            raise ValueError("Only numeric array types are supported.")

        if arr_to_convert.ndim == 1:
            arr_to_convert = np.expand_dims(arr_to_convert, axis=0)

        arr_min, arr_max = np.min(arr_to_convert), np.max(arr_to_convert)
        if arr_min == arr_max:  # handle constant values
            arr_to_convert = np.full_like(arr_to_convert, 127, dtype=np.uint8)
        else:
            arr_to_convert = ((arr_to_convert - arr_min) / (arr_max - arr_min) * 255).astype(np.uint8)

        mode = GRAYSCALE_MODE if arr_to_convert.ndim == 2 else RGB_MODE
        bytes_buffer = io.BytesIO()
        image = Image.fromarray(arr_to_convert, mode=mode)
        image.save(bytes_buffer, format=DEFAULT_IMAGE_FORMAT)
        return base64.b64encode(bytes_buffer.getvalue()).decode(DEFAULT_ENCODING)
    except ImportError:
        return "Error: Pillow library is not installed."
    except Exception as e:
        return "Error: {}".format(e)