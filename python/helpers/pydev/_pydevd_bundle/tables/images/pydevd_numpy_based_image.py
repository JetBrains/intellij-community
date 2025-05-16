#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import numpy as np
from _pydevd_bundle.tables.images.pydevd_image_loader import (save_image_to_storage, GRAYSCALE_MODE, RGB_MODE, RGBA_MODE)

try:
    import tensorflow as tf
except ImportError:
    pass
try:
    import torch
except ImportError:
    pass

def create_image(arr):
    # type: (np.ndarray) -> str
    try:
        from PIL import Image

        if hasattr(arr.dtype, 'name'):
            data_type = arr.dtype.name
        else:
            data_type = arr.dtype
        arr_to_convert = arr

        try:
            import tensorflow as tf
            if isinstance(arr_to_convert, tf.SparseTensor):
                arr_to_convert = tf.sparse.to_dense(tf.sparse.reorder(arr_to_convert))
        except ImportError:
            pass

        arr_to_convert = arr_to_convert.numpy()
        arr_to_convert = np.where(arr_to_convert == None, 0, arr_to_convert)
        arr_to_convert = np.nan_to_num(arr_to_convert, nan=0)

        if np.iscomplexobj(arr_to_convert) or np.issubdtype(arr_to_convert.dtype, np.timedelta64):
            raise ValueError("Only non-complex numeric array types are supported.")

        if arr_to_convert.ndim == 1:
            arr_to_convert = np.expand_dims(arr_to_convert, axis=0)
        elif arr_to_convert.ndim == 3 and arr_to_convert.shape[2] == 1:
            arr_to_convert = arr_to_convert[:, :, 0]

        arr_min, arr_max = arr_to_convert.min(), arr_to_convert.max()
        is_float = np.issubdtype(arr_to_convert.dtype, np.floating)
        is_bool = np.issubdtype(arr_to_convert.dtype, np.bool_)

        if (is_float or is_bool) and 0 <= arr_min <= 1 and 0 <= arr_max <= 1: # bool and float in [0; 1]
            arr_to_convert = (arr_to_convert * 255).astype(np.uint8)
        elif arr_min != arr_max and (arr_min < 0 or arr_max > 255): # other values out of [0; 255]
            arr_to_convert = ((arr_to_convert - arr_min) * 255 / (arr_max - arr_min)).astype(np.uint8)
        elif arr_min == arr_max and (arr_min < 0 or arr_max > 255):
            arr_to_convert = (np.ones_like(arr_to_convert) * 127).astype(np.uint8)
        else: # values in [0; 255]
            arr_to_convert = arr_to_convert.astype(np.uint8)

        arr_to_convert_ndim = arr_to_convert.ndim
        if arr_to_convert_ndim == 2:
            mode = GRAYSCALE_MODE
        elif arr_to_convert_ndim == 3 and arr_to_convert.shape[2] == 4:
            mode = RGBA_MODE
        else:
            mode = RGB_MODE

        return save_image_to_storage(Image.fromarray(arr_to_convert, mode=mode), data_type=data_type)

    except ImportError:
        return "Error: Pillow library is not installed."
    except (TypeError, ValueError):
        return "Error: Only non-complex numeric array types are supported."
    except Exception as e:
        return "Error: {}".format(e)
