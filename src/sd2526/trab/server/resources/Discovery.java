package sd2526.trab.server.resources;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Discovery {

  static {
    System.setProperty("java.net.preferIPv4Stack", "true");
    System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
  }

  static final public InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);
  static final int DISCOVERY_ANNOUNCE_PERIOD = 1000;
  static final int DISCOVERY_RETRY_TIMEOUT = 5000;
  static final int MAX_DATAGRAM_SIZE = 65536;
  private static final String DELIMITER = "\t";

  private final InetSocketAddress addr;
  private final String serviceName;
  private final String serviceURI;
  private final String serviceDomain;
  private final MulticastSocket ms;
  private final Map<String, Set<URI>> discoveredServices = new ConcurrentHashMap<>();

  public Discovery(String serviceName, String serviceURI, String domain) throws IOException {
    this(DISCOVERY_ADDR, serviceName, serviceURI, domain);
  }

  public Discovery() throws IOException {
    this(DISCOVERY_ADDR, null, null, null);
  }

  public Discovery(InetSocketAddress addr, String serviceName, String serviceURI, String domain) throws IOException {
    this.addr = addr;
    this.serviceName = serviceName;
    this.serviceURI = serviceURI;
    this.serviceDomain = domain;
    this.ms = new MulticastSocket(addr.getPort());
    this.ms.joinGroup(addr, NetworkInterface.getByInetAddress(InetAddress.getLocalHost()));
  }

  public void start() {
    if (this.serviceName != null && this.serviceURI != null) {
      byte[] announceBytes = String.format("%s@%s\t%s", serviceName, serviceDomain, serviceURI).getBytes();
      DatagramPacket announcePkt = new DatagramPacket(announceBytes, announceBytes.length, addr);
      new Thread(() -> {
        for (;;) {
          try {
            ms.send(announcePkt);
            Thread.sleep(DISCOVERY_ANNOUNCE_PERIOD);
          } catch (Exception e) {
          }
        }
      }).start();
    }

    new Thread(() -> {
      DatagramPacket pkt = new DatagramPacket(new byte[MAX_DATAGRAM_SIZE], MAX_DATAGRAM_SIZE);
      for (;;) {
        try {
          pkt.setLength(MAX_DATAGRAM_SIZE);
          ms.receive(pkt);
          String msg = new String(pkt.getData(), 0, pkt.getLength());
          String[] msgElems = msg.split(DELIMITER);
          if (msgElems.length == 2) {
            discoveredServices.computeIfAbsent(msgElems[0], (k) -> new HashSet<>()).add(URI.create(msgElems[1]));
            synchronized (discoveredServices) {
              discoveredServices.notifyAll();
            }
          }
        } catch (IOException e) {
        }
      }
    }).start();
  }

  public URI[] knownUrisOf(String serviceName, int minReplies) {
    synchronized (discoveredServices) {
      while (true) {
        Set<URI> uris = discoveredServices.get(serviceName);
        if (uris != null && uris.size() >= minReplies)
          return uris.toArray(new URI[0]);
        try {
          discoveredServices.wait(DISCOVERY_RETRY_TIMEOUT);
        } catch (InterruptedException e) {
        }
      }
    }
  }
}