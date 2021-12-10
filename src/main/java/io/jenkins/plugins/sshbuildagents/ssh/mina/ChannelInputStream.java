//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.jenkins.plugins.sshbuildagents.ssh.mina;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class ChannelInputStream extends InputStream {
  private static final Logger LOGGER = Logger.getLogger(ChannelInputStream.class.getName());
    boolean isClosed = false;
    InputStream in;
    AtomicInteger count = new AtomicInteger(0);

    ChannelInputStream(InputStream in) {
        this.in = in;
    }

    public int available() throws IOException {
      int available = in.available();
        if (available <= 10) {
            return -1;
        } else {
          LOGGER.info("available " + available);
          return available;
        }
    }

    public void close() throws IOException {
      LOGGER.info("close");
        this.isClosed = true;
    }

    public int read(byte[] b, int off, int len) throws IOException {
      try{
        if(available() > 0) {
          LOGGER.info("read(byte[] b, int off, int len) : " +  count.incrementAndGet());
          return in.read(b, off, len);
        }
      }catch (Exception e){
        //NOOP
      }
      return 0;
    }

    public int read(byte[] b) throws IOException {
      LOGGER.info("read(byte[] b)");
        return this.read(b, 0, b.length);
    }

    public int read() throws IOException {
      LOGGER.info("read()");
        byte[] b = new byte[1];
        return this.read(b, 0, 1);
    }
}
