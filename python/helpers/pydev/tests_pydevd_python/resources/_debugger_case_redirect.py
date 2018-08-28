# coding:utf-8
if __name__ == '__main__':
    import sys
    for stream_name in ('stdout', 'stderr'):
        stream = getattr(sys, stream_name)
        if sys.version_info[0] == 2 and sys.version_info[1] == 6:
            stream.write('text\n')
        else:
            stream.write(u'text\n')  # Can't write unicode on py 2.6
        stream.write('binary or text\n')
        stream.write('ação1\n')

        if sys.version_info[0] >= 3:
            # sys.stdout.buffer is only available on py3.   
            stream.buffer.write(b'binary\n')
            # Note: this will be giberish on the receiving side because when writing bytes
            # we can't be sure what's the encoding and will treat it as utf-8 (i.e.:
            # uses PYTHONIOENCODING).
            stream.buffer.write('ação2\n'.encode(encoding='latin1'))
            
            # This will be ok
            stream.buffer.write('ação3\n'.encode(encoding='utf-8'))

    print('TEST SUCEEDED!')