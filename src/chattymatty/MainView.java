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

import java.awt.AWTException;
import java.awt.Color;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleConstants.ColorConstants;
import javax.swing.text.StyledDocument;

/**
 *
 * @author tom
 */
public class MainView extends javax.swing.JFrame implements Client.ClientListener, ActionListener {

  private Server server = null;
  //private MenuItem serverItem;
  private MenuItem disconnectItem;
          
  private final Client client;
  private final StyledDocument chatHistoryDoc;
  private final Preferences prefs;
  Clip clip;
  
  Color [] colors = new Color[] {Color.BLACK, Color.GREEN, Color.RED, Color.ORANGE};
  
  private  TrayIcon trayIcon;
  private  SystemTray tray;
  private final Timer timer;
  /**
   * Creates new form MainView
   */
  public MainView() {
    initComponents();
    client = new Client();
    chatHistory.setText("");
    chatHistoryDoc = chatHistory.getStyledDocument();
   // chatHistoryScroll.setVerticalScrollBarPolicy(WIDTH);
    //chatHistoryScroll.
    
    prefs = Preferences.userNodeForPackage(MainView.class);
    
    timer = new Timer(5000, this);
    timer.setInitialDelay(10000);

    
    int width = prefs.getInt("width", -1);
    int height = prefs.getInt("height", -1);
    
    if (width!=-1 && height != -1) {
      this.setSize(width, height);
    }
    
    String host = prefs.get("hostname", "localhost");
    String user = prefs.get("username", "");
    
    hostname.setText(host);
    username.setText(user);
        
    returnSendsBox.setSelected(prefs.getBoolean("returnsends", false));
    setUpSound();
    setUpTray();
   // beep();
    
    localServer.setSelected(prefs.getBoolean("localServer", false));
    localServerActionPerformed(null);
 
    
    if (!host.equals("") && !user.equals("")) {
      connectGUI();
    }

    
  }

  private void setUpTray() throws HeadlessException {
    Image icon = new ImageIcon(MainView.class.getResource("icon.png"), "Chatty").getImage();
    this.setIconImage(icon);
    if (SystemTray.isSupported()) {
      trayIcon = new TrayIcon(icon);
      tray = SystemTray.getSystemTray();
      try {
        tray.add(trayIcon);
      } catch (AWTException ex) {
        Logger.getLogger(MainView.class.getName()).log(Level.SEVERE, null, ex);
      }
      final JFrame jf = this;
      trayIcon.addMouseListener(new MouseListener() {
        
        @Override
        public void mouseClicked(MouseEvent e) {
          if ((jf.getExtendedState() & JFrame.ICONIFIED) == JFrame.ICONIFIED) {
            jf.setExtendedState(jf.getExtendedState() & ~JFrame.ICONIFIED);
          } else {
            jf.setVisible(!jf.isVisible());
          }
        }
        
        @Override
        public void mousePressed(MouseEvent e) {
        }
        
        @Override
        public void mouseReleased(MouseEvent e) {
        }
        
        @Override
        public void mouseEntered(MouseEvent e) {
        }
        
        @Override
        public void mouseExited(MouseEvent e) {
        }
      });
      
      PopupMenu popup = new PopupMenu();
      popup.add(new MenuItem("ChattyMatty"));
      
      popup.addSeparator();
      
      MenuItem quitItem = new MenuItem("Exit       ");
      popup.add(quitItem);
      quitItem.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          closeThis();
        }
      });
      
      disconnectItem = new MenuItem("Disconnect");
      popup.add(disconnectItem);
      disconnectItem.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          disconnectGUI();
        }
      });
      disconnectItem.setEnabled(false);
      
      
      popup.addSeparator();
      popup.add(new MenuItem(" "));
      popup.add(new MenuItem(" "));

      trayIcon.setPopupMenu(popup);
    } else {
      System.out.println("SystemTray is not supported");
    }
  }

  private void closeThis() {
    try {
      timer.stop();
      try {
        sendNoGUI("... [disconnecting] ...");
        client.close();
      } catch (Exception ex) {
        Logger.getLogger(MainView.class.getName()).log(Level.SEVERE, null, ex);
      }
      // this.processWindowEvent(new WindowEvent(jf, WindowEvent.WINDOW_CLOSING));
      dispose();
      tray.remove(trayIcon);
      try {
        Thread.sleep(100);
      } catch (InterruptedException ex) {
        Logger.getLogger(MainView.class.getName()).log(Level.SEVERE, null, ex);
      }
      server.close();
    } catch (Throwable e) {
      
    }
    System.exit(0);    
  }
  
  private void startServer() {
    if (server==null) {
       try {
         server = new Server();
         server.start();
         do {
           try { 
             Thread.sleep(500);
           } catch (InterruptedException ex) {
             Logger.getLogger(MainView.class.getName()).log(Level.SEVERE, null, ex);
           }
         } while (!server.isReady());
         
         appendChatHistory("Server started on " + server.getAddress());
       } catch (IOException ex) {
         Logger.getLogger(MainView.class.getName()).log(Level.SEVERE, null, ex);
       }
     }    
  }
  
  
  
  
  private void setUpSound() {
    try {
      String soundName = "msgin.wav";
      AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(this.getClass().getResourceAsStream(soundName));
      DataLine.Info info = new DataLine.Info(Clip.class, audioInputStream.getFormat());
      
      clip = (Clip)AudioSystem.getLine(info); //     clip = AudioSystem.getClip();
      clip.open(audioInputStream);
    } catch (Exception ex) {
      //System.out.println(ex);
      ex.printStackTrace();
    }
  }
  
  /**
   * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of
   * this method is always regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        connectionPanel = new javax.swing.JPanel();
        localServer = new javax.swing.JCheckBox();
        jLabel2 = new javax.swing.JLabel();
        hostnameLabel = new javax.swing.JLabel();
        hostname = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        username = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        connectButton = new javax.swing.JButton();
        chatHistoryScroll = new javax.swing.JScrollPane();
        chatHistory = new javax.swing.JTextPane();
        typingPanel = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        typingArea = new javax.swing.JTextArea();
        sendButton = new javax.swing.JButton();
        returnSendsBox = new javax.swing.JCheckBox();

        jLabel1.setText("jLabel1");

        setTitle("Chatty");
        setPreferredSize(new java.awt.Dimension(570, 400));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                windowResized(evt);
            }
        });

        connectionPanel.setMinimumSize(new java.awt.Dimension(382, 50));
        connectionPanel.setPreferredSize(new java.awt.Dimension(574, 50));

        localServer.setText("Server");
        localServer.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                localServerActionPerformed(evt);
            }
        });
        connectionPanel.add(localServer);

        jLabel2.setText("  ");
        connectionPanel.add(jLabel2);

        hostnameLabel.setText("Hostname:");
        connectionPanel.add(hostnameLabel);

        hostname.setText("localhost");
        hostname.setMinimumSize(new java.awt.Dimension(4, 30));
        hostname.setName(""); // NOI18N
        hostname.setPreferredSize(new java.awt.Dimension(100, 25));
        connectionPanel.add(hostname);

        jLabel4.setText("  ");
        connectionPanel.add(jLabel4);

        jLabel3.setText("Username:");
        connectionPanel.add(jLabel3);

        username.setText("Tom");
        username.setPreferredSize(new java.awt.Dimension(100, 25));
        connectionPanel.add(username);

        jLabel5.setText("    ");
        connectionPanel.add(jLabel5);

        connectButton.setText("Connect");
        connectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                connectButtonActionPerformed(evt);
            }
        });
        connectionPanel.add(connectButton);

        getContentPane().add(connectionPanel, java.awt.BorderLayout.PAGE_START);

        chatHistoryScroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        chatHistory.setEditable(false);
        chatHistory.setFont(new java.awt.Font("Arial", 0, 18)); // NOI18N
        chatHistory.setFocusable(false);
        chatHistory.setPreferredSize(new java.awt.Dimension(40, 50));
        chatHistoryScroll.setViewportView(chatHistory);

        getContentPane().add(chatHistoryScroll, java.awt.BorderLayout.CENTER);

        typingPanel.setPreferredSize(new java.awt.Dimension(570, 90));
        typingPanel.setLayout(new java.awt.BorderLayout());

        jScrollPane2.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane2.setPreferredSize(new java.awt.Dimension(223, 60));

        typingArea.setColumns(20);
        typingArea.setFont(new java.awt.Font("Arial", 0, 18)); // NOI18N
        typingArea.setLineWrap(true);
        typingArea.setRows(4);
        typingArea.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                typingAreaKeyPressed(evt);
            }
        });
        jScrollPane2.setViewportView(typingArea);

        typingPanel.add(jScrollPane2, java.awt.BorderLayout.CENTER);

        sendButton.setText("Send");
        sendButton.setFocusable(false);
        sendButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sendButtonActionPerformed(evt);
            }
        });
        typingPanel.add(sendButton, java.awt.BorderLayout.LINE_END);

        returnSendsBox.setFont(new java.awt.Font("Dialog", 0, 10)); // NOI18N
        returnSendsBox.setSelected(true);
        returnSendsBox.setText("Enter Sends");
        returnSendsBox.setActionCommand("Enter Sends");
        returnSendsBox.setFocusable(false);
        returnSendsBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                returnSendsBoxStateChanged(evt);
            }
        });
        typingPanel.add(returnSendsBox, java.awt.BorderLayout.PAGE_END);

        getContentPane().add(typingPanel, java.awt.BorderLayout.PAGE_END);

        pack();
    }// </editor-fold>//GEN-END:initComponents

  private void connectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_connectButtonActionPerformed
    connectGUI();
  }//GEN-LAST:event_connectButtonActionPerformed

  
  private void connectGUI() {
    if (username.getText().equals("")) {
      JOptionPane.showMessageDialog(this, "Must enter a username.");
      username.requestFocusInWindow();
      return;
    }
    try {
      connect();
      send("...[Connected]...");
      prefs.put("hostname", hostname.getText());
      prefs.put("username", username.getText());
      prefs.putBoolean("localServer", localServer.isSelected());
      connectionPanel.setVisible(false);
      
    } catch (IOException ex) {
      Logger.getLogger(MainView.class.getName()).log(Level.SEVERE, null, ex);
      appendChatHistory(ex.getMessage() + "\n");
      connectionPanel.setVisible(true);
      return;
    }
   // connectionPanel.setVisible(false);
    typingArea.requestFocus();
    typingArea.requestFocusInWindow();
  }
  
  private void connect() throws IOException {
    if (localServer.isSelected()) startServer();
    client.connect(hostname.getText(), username.getText(), this);
    disconnectItem.setEnabled(true);
    timer.start(); 

  }

  private void disconnectGUI() {
    connectionPanel.setVisible(true);
    try {
      timer.stop(); 
      send("...[Disconnecting]...");
      client.close();
      disconnectItem.setEnabled(false);
      if (server!=null) {
        server.close();
        server = null;
        appendChatHistory("Server stopped\n");
      }
    } catch (Exception ex) {
      Logger.getLogger(MainView.class.getName()).log(Level.SEVERE, null, ex);
    }
  }
  
  
  
  private void appendChatHistory(String text) {
    appendChatHistory(text, new SimpleAttributeSet());
  }
 
  private void appendChatHistory(String text, SimpleAttributeSet attributes) {
    try {    
      chatHistoryDoc.insertString(chatHistoryDoc.getLength(), text, attributes);
      chatHistory.setCaretPosition(chatHistoryDoc.getLength());
    } catch (BadLocationException ex) {
      Logger.getLogger(MainView.class.getName()).log(Level.SEVERE, null, ex);
    }
  }
  private void send() {
    send(typingArea.getText(), true);
  }

  private void send(String text) {
    send(text, true);
  }

  private void sendNoGUI(String text) {
    send(text, false);
  }
  
  private void send(String text, boolean dogui) {
    if (text == null || text.equals("")) {
      return;
    }
    try {
      try {
        client.send(text);
      } catch(IOException ex) {
        System.out.println(ex);
        System.out.println("reconnecting");
        try{ 
          client.close();
          Thread.sleep(1000);
        } catch (Exception e) {
          System.out.println(ex);
        }
        connect();
        client.send(text);
      }
      if (dogui) {
        typingArea.setText("");
        typingArea.requestFocus();
        typingArea.requestFocusInWindow();
      }
    } catch (IOException ex) {
      Logger.getLogger(MainView.class.getName()).log(Level.SEVERE, null, ex);
      if (dogui) appendChatHistory(ex.getMessage() + "\n");
    }   
  }
  
  private void sendButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sendButtonActionPerformed
    send();
  }//GEN-LAST:event_sendButtonActionPerformed

  private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
    try {

      client.close();
    } catch (Exception ex) {
      Logger.getLogger(MainView.class.getName()).log(Level.SEVERE, null, ex);
    }
  }//GEN-LAST:event_formWindowClosed

  private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
    typingArea.requestFocus();
    typingArea.requestFocusInWindow();
  }//GEN-LAST:event_formWindowOpened

  private void typingAreaKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_typingAreaKeyPressed
    //beep();
    if (returnSendsBox.isSelected() && evt.getKeyCode() == KeyEvent.VK_ENTER) {
      send();
      evt.consume();
    }

  }//GEN-LAST:event_typingAreaKeyPressed

  private void returnSendsBoxStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_returnSendsBoxStateChanged
    prefs.putBoolean("returnsends", returnSendsBox.isSelected());
  }//GEN-LAST:event_returnSendsBoxStateChanged

  private void windowResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_windowResized
    prefs.putInt("width", this.getWidth());
    prefs.putInt("height", this.getHeight());
  }//GEN-LAST:event_windowResized

  private void localServerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_localServerActionPerformed
//    appendChatHistory(localServer.isSelected() + "\n");
    boolean showHostname = true;
    if (localServer.isSelected()) {
      hostname.setText("localhost");
      showHostname = false;
    }
    hostnameLabel.setVisible(showHostname);
    hostname.setVisible(showHostname);
  }//GEN-LAST:event_localServerActionPerformed

  @Override
  protected void finalize() throws Throwable {
    try {
      client.close();
    } catch (Exception ex) {
      Logger.getLogger(MainView.class.getName()).log(Level.SEVERE, null, ex);
    }
    super.finalize();
    
  }
  
  private HashMap<String,Integer> usercolor = new HashMap<>();
  private int usernum = 0;
  @Override
  public void messageReceived(String name, String message) {

    String textlog = "";
    
    SimpleDateFormat sf = new SimpleDateFormat("y/M/d HH:mm");
    

    SimpleAttributeSet dateattrs = new SimpleAttributeSet();
    dateattrs.addAttribute(StyleConstants.FontSize, 10);
   // dateattrs.addAttribute(StyleConstants.Subscript, true);

    appendChatHistory(" \n[ " + sf.format(new Date()) + " ]\n", dateattrs);

    textlog += sf.format(new Date());
    
    SimpleAttributeSet nameattrs = new SimpleAttributeSet();
    nameattrs.addAttribute(StyleConstants.Bold, Boolean.TRUE);
    SimpleAttributeSet messagearttrs = new SimpleAttributeSet();
    
    Color color = Color.BLACK;
    if (name.equals(client.userName)) {
      color = Color.BLUE;
      
    } else {
      Integer ucolor = usercolor.get(name);
      if (ucolor==null) {
        ucolor = usernum++ % colors.length;
        usercolor.put(name, ucolor);
      }
      color = colors[ucolor];
      beep();
      
    }

    nameattrs.addAttribute(ColorConstants.Foreground, color);
    messagearttrs.addAttribute(ColorConstants.Foreground, color);
            
    appendChatHistory(name + ":  ", nameattrs);
    textlog += " " + name + ": ";

    
    appendChatHistory(message + "\n", messagearttrs);
    textlog += message + "\n";
    
    File base = new File(System.getProperty("user.home"), ".chatty");
    if (!base.exists()) {
      base.mkdirs();
    }
    File file = new File(base, "history.txt");
    
    File lockdir = new File(base, "lock");
    int tries = 0;
    while(lockdir.exists() && tries++<10) {
      try {
        Thread.sleep(10);
      } catch (InterruptedException ex) {
      }
    }
    try {
      lockdir.mkdir();
      try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), Charset.forName("UTF-8"), CREATE, APPEND)) {
        writer.write(textlog);
      } catch (IOException x) {
          System.err.format("IOException: %s%n", x);
      }
    } finally {
      lockdir.delete();
    }
    

    
    if (!this.isFocused()) {
    }
  }

  private void beep() {
    if (!this.isShowing() || (this.getExtendedState() & JFrame.ICONIFIED) == JFrame.ICONIFIED) {
      trayIcon.displayMessage("Chatty", "Message recieved", TrayIcon.MessageType.INFO);
    }
    //Beep
    try {
      //chatHistory.setBackground(Color.yellow);
      clip.setFramePosition(0);
     // clip.loop(0);
      clip.start();
      //do {
        Thread.sleep(150);
      //} while (clip.isRunning());
     // chatHistory.setBackground(Color.WHITE);

    } catch (Exception e) {
      System.out.println(e);
    }

  }
  
  @Override
  public void actionPerformed(ActionEvent e) {
    try {
      boolean pinged = false;
      for (int i = 0; i < 2; i++) {
        pinged = true;
        if (client.ping()) {
          break;
        }
        pinged = false;
        Thread.sleep(500);
      }
      if (!pinged) {
        try {
          client.close();
        } catch (Exception ex) {
          Logger.getLogger(MainView.class.getName()).log(Level.SEVERE, null, ex);
        }
        Thread.sleep(1000);
        connectGUI();
      }
    } catch (Exception ex) {

    }

  }


  /**
   * @param args the command line arguments
   */
  public static void main(String args[]) {
    /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
     * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
     */
    try {
      for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
        if ("Metal".equals(info.getName())) {
          javax.swing.UIManager.setLookAndFeel(info.getClassName());
          break;
        }
      }
    } catch (ClassNotFoundException ex) {
      java.util.logging.Logger.getLogger(MainView.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
    } catch (InstantiationException ex) {
      java.util.logging.Logger.getLogger(MainView.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
    } catch (IllegalAccessException ex) {
      java.util.logging.Logger.getLogger(MainView.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
    } catch (javax.swing.UnsupportedLookAndFeelException ex) {
      java.util.logging.Logger.getLogger(MainView.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
    }
        //</editor-fold>

    /* Create and display the form */
    java.awt.EventQueue.invokeLater(new Runnable() {
      @Override
      public void run() {
        new MainView().setVisible(true);
      }
    });
  }

  
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextPane chatHistory;
    private javax.swing.JScrollPane chatHistoryScroll;
    private javax.swing.JButton connectButton;
    private javax.swing.JPanel connectionPanel;
    private javax.swing.JTextField hostname;
    private javax.swing.JLabel hostnameLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JCheckBox localServer;
    private javax.swing.JCheckBox returnSendsBox;
    private javax.swing.JButton sendButton;
    private javax.swing.JTextArea typingArea;
    private javax.swing.JPanel typingPanel;
    private javax.swing.JTextField username;
    // End of variables declaration//GEN-END:variables


}
