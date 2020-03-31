'''
Get windows_ip:wsl_ip
'''

import socket
import re

with open("/etc/resolv.conf") as f:
    match = re.search("^nameserver +(?P<win_ip>[0-9.]+)$", f.read(), re.MULTILINE)
    if match:
        win_ip = match.group("win_ip")
    else:
        raise Exception("Can't find windows ip in resolv.conf")

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.connect((win_ip, 1))
lin_ip = s.getsockname()[0]

result = "{0}:{1}".format(win_ip, lin_ip)
print(result)
