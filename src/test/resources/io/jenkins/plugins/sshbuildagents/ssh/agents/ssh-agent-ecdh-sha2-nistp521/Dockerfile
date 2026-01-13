FROM ghcr.io/jenkinsci/ssh-agents-plugin:base48d6d44@sha256:5bd4d812910268d9ad217058dcb4190ca8ba6eb36de43ac5cccf776eec510486
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
