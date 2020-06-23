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