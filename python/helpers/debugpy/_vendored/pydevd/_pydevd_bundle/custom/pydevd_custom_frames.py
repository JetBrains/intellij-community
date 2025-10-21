def get_custom_frame(thread_id, frame_id):
    '''
    :param thread_id: This should actually be the frame_id which is returned by add_custom_frame.
    :param frame_id: This is the actual id() of the frame
    '''

    CustomFramesContainer.custom_frames_lock.acquire()
    try:
        frame_id = int(frame_id)
        f = CustomFramesContainer.custom_frames[thread_id].frame
        while f is not None:
            if id(f) == frame_id:
                return f
            f = f.f_back
    finally:
        f = None
        CustomFramesContainer.custom_frames_lock.release()