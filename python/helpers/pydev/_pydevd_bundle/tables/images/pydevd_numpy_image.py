#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import numpy as np
from _pydevd_bundle.tables.images.pydevd_image_loader import (save_image_to_storage, GRAYSCALE_MODE, RGB_MODE, RGBA_MODE)

def create_image(arr):
    # type: (np.ndarray) -> str
    try:
        from PIL import Image

        arr_to_convert = arr
        arr_to_convert = np.where(arr_to_convert == None, 0, arr_to_convert)
        arr_to_convert = np.nan_to_num(arr_to_convert, nan=0)

        if np.iscomplexobj(arr_to_convert) or np.issubdtype(arr_to_convert.dtype, np.timedelta64):
            raise ValueError("Only non-complex numeric array types are supported.")

        if arr_to_convert.ndim == 1:
            arr_to_convert = np.expand_dims(arr_to_convert, axis=0)
        elif arr_to_convert.ndim == 3 and arr_to_convert.shape[2] == 1:
            arr_to_convert = arr_to_convert[:, :, 0]

        arr_min, arr_max = np.min(arr_to_convert), np.max(arr_to_convert)
        if arr_min == arr_max:  # handle constant values
            arr_to_convert = np.full_like(arr_to_convert, 127, dtype=np.uint8)
        elif 0 <= arr_min <= 1 and 0 <= arr_max <= 1:
            arr_to_convert = (arr_to_convert * 255).astype(np.uint8)
        elif arr_min < 0 or arr_max > 255:
            arr_to_convert = ((arr_to_convert - arr_min) * 255 / (arr_max - arr_min)).astype(np.uint8)
        else:
            arr_to_convert = arr_to_convert.astype(np.uint8)

        arr_to_convert_ndim = arr_to_convert.ndim
        if arr_to_convert_ndim == 2:
            mode = GRAYSCALE_MODE
        elif arr_to_convert_ndim == 3 and arr_to_convert.shape[2] == 4:
            mode = RGBA_MODE
        else:
            mode = RGB_MODE

        return save_image_to_storage(Image.fromarray(arr_to_convert, mode=mode))

    except ImportError:
        return "Error: Pillow library is not installed."
    except (TypeError, ValueError):
        return "Error: Only non-complex numeric array types are supported."
    except Exception as e:
        return "Error: {}".format(e)
