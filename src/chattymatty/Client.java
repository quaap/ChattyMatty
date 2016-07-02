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
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author tom
 */
public class Client implements AutoCloseable {

  String hostName;
  String userName;
  Socket server;
  
  ClientListener clistener;
  Thread listener;
  
  public Client() {
    
  }

  public void connect(String hostName, String username, ClientListener clistener) throws IOException {
    this.hostName = hostName;
    this.userName = username;
    this.clistener = clistener;
    server = new Socket(hostName, Server.PORT);
    
    Protocol.sendName(server.getOutputStream(), username);
    
    listen();

  }

  
  public void send(String message) throws IOException {
    if (Server.DEBUG>0) System.out.println("Client sent " + message);
    Protocol.sendMessage(server.getOutputStream(), userName, message);
  }
  
  public boolean ping() {
    if (Server.DEBUG>2) 
      System.out.println("Client sent ping");
    try {
      Protocol.ping(server.getOutputStream());
    } catch (IOException e) {
      System.out.println(e);
      return false;
    }
    return true;
  }
  
  public void listen() {
    listener = new Thread() {
      
      @Override
      public void run() {
        try {
          while (!server.isClosed()) {
            Protocol.Message message = Protocol.receiveMessage(server.getInputStream());
            if (message == null) {
              clistener.messageReceived("Server", "Connection closed");
              server.close();
            } else {
           // while ((inputLine = in.readLine()) != null) {
              if (Server.DEBUG>0) System.out.println("Client received " + message.message + " from " + message.name);
              clistener.messageReceived(message.name, message.message);
            }
           // }
          }
        } catch (IOException ex) {
          Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
      }
      
    };
    listener.start();

  }

  @Override
  public void close() throws Exception {
    try {
      server.close();
    } finally {
      listener.interrupt();
    }

  }
  
  
  public static interface ClientListener {
    public void messageReceived(String name, String message);
  }
}
