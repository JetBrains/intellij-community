import csv
csv.register_dialect('unixpwd', delimiter=':', quoting=csv.QUOTE_NONE)