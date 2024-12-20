FROM ghcr.io/jenkinsci/ssh-agents-plugin:baseb2fb086@sha256:2bc6d82ca2b406b0a2b509fd2308c38ae51de9f3e61cb1288e3421f6116bb27c
USER root

COPY ssh /home/jenkins/.ssh
RUN chown -R jenkins:jenkins /home/jenkins/ \
  && chmod 700 /home/jenkins/.ssh \
  && chmod 600 /home/jenkins/.ssh/*
COPY ssh /root/.ssh
RUN chown -R root:root /root/ \
  && chmod 700 /root/.ssh \
  && chmod 600 /root/.ssh/*
COPY ssh/sshd_config /etc/ssh/sshd_config

RUN rm /etc/ssh/ssh_host_ed* /etc/ssh/ssh_host_rsa*
