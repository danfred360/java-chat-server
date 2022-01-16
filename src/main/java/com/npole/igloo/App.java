package com.npole.igloo;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class App 
{
    private static int UID;
    private ArrayList<ClientThread> client_threads;
    private SimpleDateFormat sdf;
    private int port;
    private boolean is_running;

    public App(int port) {
        this.port = port;
        sdf = new SimpleDateFormat("HH:mm:ss");
        client_threads = new ArrayList<ClientThread>();
    }

    public void announce(String message) {
        for (ClientThread client: client_threads)
            client.write(message);
    }

    public void start() {
        is_running = true;
        // create socket server and wait for connection requests
        try {
            ServerSocket server_socket = new ServerSocket(port);

            while(is_running) {
                display("server waiting for Clients on port " + port + ".");

                Socket socket = server_socket.accept();
                if (!is_running)
                    break;
                ClientThread thread = new ClientThread(socket);
                client_threads.add(thread);
                thread.start();
            }
            try {
                server_socket.close();
                for (int i = 0; i < client_threads.size(); i++) {
                    ClientThread client_thread = client_threads.get(i);
                    try {
                        client_thread.socket_input.close();
                        client_thread.socket_output.close();
                        client_thread.socket.close();
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            } catch (Exception e) {
                display("Exception closing the server and clients: \n" + e + "\n");
            }
        } catch (IOException e) {
            String msg = sdf.format(new Date()) + "Exception on new server socket: \n" + e + "\n";
            display(msg);
        }
    }

    protected void stop() {
        is_running = false;
        try {
            new Socket("localhost", port);
        } catch (Exception e) {
            // do nothing
        }
    }

    public void display(String str) {
        String msg = sdf.format(new Date()) + " " + str;
        System.out.println(msg);
    }

    private synchronized void broadcast(String msg) {
        String time = sdf.format(new Date());
        String message = time + " " + msg + "\n";

        System.out.print(message);

        for (int i = client_threads.size(); --i >= 0;) {
            ClientThread client_thread = client_threads.get(i);
            if (!client_thread.write(message)) {
                client_threads.remove(i);
                display(client_thread.username + " has disconnected.");
            }
        }
    }

    // called when client logs out using LOGOUT message
    synchronized void remove(int client_id) {
        for (int i = 0; i < client_threads.size(); i ++) {
            ClientThread client_thread = client_threads.get(i);
            if (client_thread.id == client_id) {
                client_threads.remove(i);
                return;
            }
        }
    }

    /*
    * To run as a console application just open a console window and: 
    * > java Igloo
    * > java Igloo portNumber
    * If the port number is not specified 8000 is used
    */ 
    public static void main( String[] args ) {
        int port = 8000;
        switch(args.length) {
            case 1:
                try {
                    port = Integer.parseInt(args[0]);
                } catch (Exception e) {
                    System.out.println("Invalid port number.");
                    System.out.println("Usage is > java Igloo [portNumber]");
                    return;
                }
            case 0:
                break;
            default:
                System.out.println("Usage is > java Igloo [portNumber]");
                return;
        }

        App app = new App(port);
        app.start();
    }

    class ClientThread extends Thread {
        Socket socket;
        ObjectInputStream socket_input;
        ObjectOutputStream socket_output;
        int id;
        String username;
        ChatMessage cm;
        String date;

        ClientThread(Socket socket) {
            id = ++UID;
            this.socket = socket;

            try {
                socket_input = new ObjectInputStream(socket.getInputStream());
                socket_output = new ObjectOutputStream(socket.getOutputStream());
                username = (String) socket_input.readObject();
                display(username + " has joined the chat.");
                announce(username + " has joined the chat.\nEnter 'WHOISIN' to see list of connected users.");
            } catch (IOException e) {
                display("Exception creating new I/O streams: " + e);
                return;
            } catch (ClassNotFoundException e) {
                // do nothing
            }
            date = new Date().toString() + "\n";
        }

        public void run() {
            boolean is_running = true;
            while (is_running) {
                try {
                    cm (ChatMessage) socket_input.readObject();
                } catch (IOException e) {
                    display("Exception reading input stream for user '" + username + "':\n" + e);
                    break;
                } catch (ClassNotFoundException e2) {
                    break;
                }
                String message = cm.getMessage();

                switch(cm.getType()) {
                    case ChatMessage.MESSAGE:
                        broadcast(username + ": " + message);
                        break;
                    case ChatMessage.LOGOUT:
                        display(username + " intentionally disconected.");
                        announce(username + " has disconnected.");
                        is_running = false;
                        break;
                    case ChatMessage.WHOISIN:
                        announce(username + " has requested the user list:\n");
                        for (int i = 0; i < client_threads.size(); ++i) {
                            announce(client_threads.get(i).username);
                        }
                        break;
                }
            }
            
            remove(id);
            announce(username + " has disconnected.");
            close();
        }

        private void close() {
            try {
                if (socket_output != null) socket_output.close();
            } catch (Exception e) {
                // do nothing
            }
            try {
                if (socket_input != null) socket_input.close();
            } catch (Exception e) {
                // do nothing
            }
            try {
                if (socket != null) socket.close();
            } catch (Exception e) {
                // do nothing
            }
        }

        // write a string to the client output stream
        public boolean write(String msg) {
            if (!socket.isConnected()) {
                close();
                return false;
            }

            try {
                socket_output.writeObject(msg);
            } catch (IOException e) {
                display("Exception writing to the output stream for user " + username + ":\n" + e);
            }

            return true;
        }
    }
}
