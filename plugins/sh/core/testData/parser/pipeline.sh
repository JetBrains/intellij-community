ipseckey -n monitor > $MONITOR_LOG ;
IPSECKEY_PID=$!

ipseckey -n monitor > $MONITOR_LOG &
IPSECKEY_PID=$!

# Now try some telnets to trigger port and unique policy.
# port-only for DST3
telnet $TEST_REMOTE_DST3 &
tpid=$!

foo() {
  echo "test" |& tee $Log
}