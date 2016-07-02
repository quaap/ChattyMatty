/*
 * Copyright (c) 2012, Tom Kliethermes
 * 
 * This is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this file.  If not, see <http://www.gnu.org/licenses/>.
 */
package chattymatty;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author tom
 */
public class Server extends Thread {

  public final static int DEBUG = 0;

  public static final int PORT = 2322;

  public final List<ClientHandler> clients = new ArrayList<>();

  final private ServerSocket serverSocket;

  public Server() throws IOException {
    serverSocket = new ServerSocket(PORT);

  }
  
  public String getAddress() {
    List<String> addrs = new ArrayList<>();
    try {
      addrs.add(InetAddress.getLocalHost().getHostName());
    } catch (UnknownHostException ex) {
      Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
    }
    try {
      
      Enumeration e=NetworkInterface.getNetworkInterfaces();
      while(e.hasMoreElements())
      {
        NetworkInterface n=(NetworkInterface) e.nextElement();
        if (!n.isLoopback() && n.isUp()) {
          for(InterfaceAddress ia: n.getInterfaceAddresses()) {
            
            if (ia.getBroadcast()!=null) {
              addrs.add(ia.getAddress().getHostAddress());

            }
          }
        }
      }
      
    } catch (SocketException ex) {
      Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
    }
    return join(addrs, ", ");
  }

  @Override
  public void run() {
    if (DEBUG > 0) {
      System.out.println("Starting server");
    }
    while (!serverSocket.isClosed()) {
      try {
        Socket clientSocket = serverSocket.accept();
        clientSocket.setKeepAlive(true);

        ClientHandler clientHandler = new ClientHandler(clientSocket);
        clientHandler.start();
        synchronized (clients) {
          clients.add(clientHandler);
        }
      } catch (IOException ex) {
        Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
  }

  public boolean isReady() {
    return serverSocket.isBound();
  }
  
  public void sendToClients(String name, String message) {
    List<ClientHandler> deadclients = new ArrayList<>();
    synchronized (clients) {
      for (ClientHandler c : clients) {
        try {
          if (DEBUG > 1) System.out.println("sending to client " + c);
          c.send(name, message);
          if (DEBUG > 1) System.out.println("sent to client " + c);
        } catch (Exception e) {
          if (DEBUG > 0) System.out.println(e.getMessage());
          deadclients.add(c);
          try {
            c.close();
          } catch (Exception e2) {
          }
        }
      }
      clients.removeAll(deadclients);
    }

  }

  
  public void close() {
    synchronized (clients) {
      for (ClientHandler c : clients) {
        try {
          c.close();
        } catch (Exception ex) {
          Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
      }
      try {
        serverSocket.close();
      } catch (IOException ex) {
        Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
    clients.clear();
    
  }
  
  public class ClientHandler extends Thread implements AutoCloseable {

    final private Socket client;

    private String name;

    public ClientHandler(Socket client) throws IOException {
      if (DEBUG > 0) {
        System.out.println("New client " + client);
      }
      this.client = client;

    }

    public synchronized void send(String name, String text) throws IOException {
      Protocol.sendMessage(client.getOutputStream(), name, text);
    }

    @Override
    public void close() {
      try {
        client.close();
      } catch (IOException ex) {
        Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);

      }
    }


    @Override
    public void run() {

      Protocol.Message message;
      try {
        name = Protocol.receiveName(client.getInputStream());
        boolean done = false;
        while (!done && !client.isInputShutdown() && !client.isClosed()) {

          if ((message = Protocol.receiveMessage(client.getInputStream())) != null) {

            if (DEBUG > 0) {
              System.out.println("Server received " + message.message + " from " + name +" as " + message.name );
            }
            sendToClients(name, message.message);

          } else {
            done = true;
          }

        }

      } catch (IOException ex) {
        Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
      } finally {
        try {
         // reader.close();
          //  writer.close();
          client.close();
        } catch (IOException ex1) {
          Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex1);
        }
      }

    }

  }
  
  static public String join(List<String> list, String conjunction) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (String item : list) {
      if (first) {
        first = false;
      } else {
        sb.append(conjunction);
      }
      sb.append(item);
    }
    return sb.toString();
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    Server server = new Server();
    server.run();

  }
}
