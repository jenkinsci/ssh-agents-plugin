FROM ubuntu:20.04
USER root

ENV TZ=Etc/UTC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
RUN DEBIAN_FRONTEND="noninteractive" apt-get update -y -qq \
  &&  apt-get install -y -qq \
    --no-install-recommends \
    openssh-server \
    software-properties-common \
    git \
    make

RUN add-apt-repository ppa:openjdk-r/ppa -y \
  && apt-get update -y -qq \
  && apt-get install -y -qq \
    openjdk-11-jdk \
  && rm -rf /var/lib/apt/lists/*

RUN useradd --password password --shell /bin/bash --uid 1000 jenkins \
  && mkdir /home/jenkins \
  && chown -R jenkins:jenkins /home/jenkins

COPY ssh /home/jenkins/.ssh
RUN chown -R jenkins:jenkins /home/jenkins/ \
  && chmod 700 /home/jenkins/.ssh \
  && chmod 600 /home/jenkins/.ssh/*
COPY ssh /root/.ssh
RUN chown -R root:root /root/ \
  && chmod 700 /root/.ssh \
  && chmod 600 /root/.ssh/*
RUN ssh-keygen -A
COPY ssh/sshd_config /etc/ssh/sshd_config

RUN mkdir -p  /var/run/sshd

RUN echo "password\npassword" | passwd root \
  && echo "password\npassword" | passwd jenkins

EXPOSE 22
ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/lib/jvm/java-11-openjdk-amd64/jre/bin:/usr/lib/jvm/java-11-openjdk-amd64/bin
RUN echo "PATH=${PATH}" >> /etc/environment
ENTRYPOINT []
CMD [ "/bin/sh", "-c", "/usr/sbin/sshd -e -D -p 22"]

RUN rm /etc/ssh/ssh_host_dsa* /etc/ssh/ssh_host_ed* /etc/ssh/ssh_host_ec*
