#!/bin/sh
WHO=world

# types of quoting
cat << EOF
hello $WHO
hello ${WHO}
hello `echo $WHO`
hello $(echo $WHO)
EOF

cat << "EOF"
hello $WHO
hello ${WHO}
hello `echo $WHO`
hello $(echo $WHO)
EOF

cat << 'EOF'
hello $WHO
hello ${WHO}
hello `echo $WHO`
hello $(echo $WHO)
EOF

# types of quoting, with <<-
cat <<-EOF
hello $WHO
hello ${WHO}
hello `echo $WHO`
hello $(echo $WHO)
EOF

cat <<-"EOF"
hello $WHO
hello ${WHO}
hello `echo $WHO`
hello $(echo $WHO)
EOF

cat <<-'EOF'
hello $WHO
hello ${WHO}
hello `echo $WHO`
hello $(echo $WHO)
EOF

# multiple heredocs
cat << EOF1 <<-EOF2 <<-'EOF3'
not shown
EOF1
not shown
EOF2
shown without var replacement $WHO
EOF3

# complex heredocs
while read myLine; do
  echo $myLine
done << 'EOF' && cat - << EOF2
  line 1
  line 2
  line 3
EOF
  Hello $WHO
  cat line 1
  cat line 2
EOF2