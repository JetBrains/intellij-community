#!/bin/bash

tee '/lib/systemd/system/add-localhost.service' <<EOF
[Unit]
Description=Adds hostname to /etc/hosts
After=network.target

[Service]
Type=oneshot
ExecStart=-/etc/add-localhost.sh

[Install]
WantedBy=default.target
EOF

mkdir -p "/etc/systemd/system/${MAIN_SERVICE}.service.d"
tee "/etc/systemd/system/${MAIN_SERVICE}.service.d/add-localhost.conf" <<'EOF'
[Unit]
After=add-localhost.service
EOF

systemctl daemon-reload
systemctl enable add-localhost.service

tee '/etc/add-localhost.sh' <<'EOF'
#!/bin/bash
echo "========================================"
echo "$(date)"

sed -ie '/127.0.0.1 ip\-/d' /etc/hosts
echo "127.0.0.1 $(hostname -s)" >> /etc/hosts;

EOF

chmod +x '/etc/add-localhost.sh'