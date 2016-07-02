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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author tom
 */
public class Protocol {
    
    public static int ENDTYPE= -1;
    public static int NAMETYPE=0;
    public static int MESSAGETYPE=1;
    public static int PINGTYPE=2;
    
    
    public static class Message {
      final public String name;
      final public String message;

      public Message(String name, String message) {
        this.name = name;
        this.message = message;
      }
      
    }
    
    private static void sendMSG(OutputStream out, String name, int type) throws IOException{
      try {
        synchronized(out) {
          out.write(type);
          byte[] namebuff = name.getBytes("UTF-8");
          out.write(namebuff.length);
          out.write(namebuff);
        }
        
      } catch (UnsupportedEncodingException ex) {
        Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
      }
      
    }
    
    private  static String receiveMSG(InputStream in, int type) throws IOException{

      synchronized(in) {
        int rtype;
        do { 
          rtype = in.read();  
        } while (rtype == PINGTYPE); // ignore our keepalive pings

        if (rtype == -1) {
          return null;
        }
        if (rtype == type) {
          int len = in.read();
          byte [] strbuff = new byte[len];
          in.read(strbuff);
          return new String(strbuff, "UTF-8");

        } else {
          throw new IOException("Not correct type " + rtype + "==" +  type);
        }
      }

    }
    
    public static void sendMessage(OutputStream out, String name, String text) throws IOException{
      synchronized(out) {
        sendMSG(out, name, NAMETYPE);
        sendMSG(out, text, MESSAGETYPE);
      }
      
    }
    
    public static Message receiveMessage(InputStream in) throws IOException {
      synchronized(in) {
        String name = receiveMSG(in, NAMETYPE);
        if (name==null) {
          return null;
        }
        String message = receiveMSG(in, MESSAGETYPE);
        if (message==null) {
          return null;
        }
        return new Message(name, message);
      }
        
     }
        
    public static void sendName(OutputStream out, String name) throws IOException {
        synchronized(out) {
          sendMSG(out, name, NAMETYPE);
        }
     
    }
    
    public static String receiveName(InputStream in) throws IOException {
       synchronized(in) {
         return receiveMSG(in, NAMETYPE);
       }
    }
    
    public static void ping(OutputStream out) throws IOException {
       synchronized(out) {
         out.write(PINGTYPE);
       }
    }
    
  }