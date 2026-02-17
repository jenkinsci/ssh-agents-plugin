FROM ubuntu:22.04 AS base

# install SSHD
RUN apt update -y && \
    apt install -y \
        openssh-server \
        locales \
        ucommon-utils \
        coreutils

# Create an SSH user
RUN useradd -rm -d /home/foo -s /bin/bash -g root -G sudo -u 1000 foo
# Set the SSH user's password (replace "password" with your desired password)
RUN echo 'foo:beer' | chpasswd

# force X25519 which is not FIPS compliant
RUN echo "KexAlgorithms curve25519-sha256,curve25519-sha256@libssh.org" >> /etc/ssh/sshd_config

RUN echo 'HostKeyAlgorithms ssh-ed25519,rsa-sha2-512,rsa-sha2-256,ecdsa-sha2-nistp256' >> /etc/ssh/sshd_config


# Allow SSH access
RUN mkdir /var/run/sshd

USER foo

COPY --from=eclipse-temurin:21.0.9_10-jdk /opt/java/openjdk /usr/java/latest
# Expose the SSH port
EXPOSE 22

COPY rsa2048.pub.ssh rsa2048.pub.ssh
RUN mkdir /home/foo/.ssh/
RUN cat rsa2048.pub.ssh >> /home/foo/.ssh/authorized_keys

USER root

# Start SSH server on container startup
CMD ["/usr/sbin/sshd", "-D"]

FROM base AS ed25519
RUN sed -i 's/HostKeyAlgorithms.*/HostKeyAlgorithms ssh-ed25519,rsa-sha2-512,rsa-sha2-256,ecdsa-sha2-nistp256/g' /etc/ssh/sshd_config

FROM base AS fips
RUN sed -i 's/KexAlgorithms.*/KexAlgorithms diffie-hellman-group14-sha256,diffie-hellman-group-exchange-sha256/g' /etc/ssh/sshd_config
