import re

re.compile(r"(?P<group1>delicious )?(?(group1)hamburger|milkshake)");
re.compile(r"(?P<group1>delicious )?(?(1)hamburger|milkshake)");
re.compile(r"(?P<group1>delicious )?(?<error descr="This kind of group reference condition is not supported in this regex dialect">('group1')</error>hamburger|milkshake)");
re.compile(r"<error descr="This named group syntax is not supported in this regex dialect">(?<group1>delicious )</error>?(?<error descr="This kind of group reference condition is not supported in this regex dialect">(<group1>)</error>hamburger|milkshake)");
re.compile(r"<error descr="This named group syntax is not supported in this regex dialect">(?'group1'delicious )</error>?(?(group1)hamburger|milkshake)");