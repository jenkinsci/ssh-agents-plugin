FROM ubuntu:24.04
USER root

ENV TZ=Etc/UTC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
RUN add-apt-repository ppa:openjdk-r/ppa -y
RUN DEBIAN_FRONTEND="noninteractive" apt-get update -y -qq \
  && DEBIAN_FRONTEND="noninteractive" apt-get install -y -qq \
    --no-install-recommends \
    openssh-server \
    software-properties-common \
    git \
    make \
    openjdk-17-jdk \
  && rm -rf /var/lib/apt/lists/*

RUN useradd --password password --shell /bin/bash jenkins \
  && mkdir /home/jenkins \
  && chown -R jenkins:jenkins /home/jenkins

RUN mkdir -p  /var/run/sshd

RUN echo "password\npassword" | passwd root \
  && echo "password\npassword" | passwd jenkins

RUN ssh-keygen -A

EXPOSE 22
ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/lib/jvm/java-17-openjdk-amd64/jre/bin:/usr/lib/jvm/java-17-openjdk-amd64/bin
RUN echo "PATH=${PATH}" >> /etc/environment
ENTRYPOINT []
CMD [ "/bin/sh", "-c", "/usr/sbin/sshd -e -D -p 22"]
