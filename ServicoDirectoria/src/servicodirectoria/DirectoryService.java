/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package servicodirectoria;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author andre
 */

public class DirectoryService extends Thread{

    public static final String TIME_REQUEST = "TIME";
    public static final int MAX_SIZE = 10000;
    
    private DatagramSocket socket;
    private DatagramPacket packet; //para receber os pedidos e enviar as respostas
    
    private List<Server_Registry> activeServers;

    public DirectoryService(int listeningPort) throws SocketException
    {
        socket = null;
        packet = new DatagramPacket(new byte[MAX_SIZE], MAX_SIZE);        
        socket = new DatagramSocket(listeningPort);
        activeServers = new ArrayList<>();
    }

    public List<Server_Registry> getActiveServers() {
        synchronized(activeServers) {
            return activeServers;
        }
    }
    
    
    
    public String waitDatagram() throws IOException
    {
        String request;
        ObjectInputStream in;
        
        if(socket == null){
            return null;
        }
        
        socket.receive(packet);
        in = new ObjectInputStream(new ByteArrayInputStream(packet.getData()));

        try{
            request = (String)(in.readObject());
        }catch(ClassCastException | ClassNotFoundException e){
             System.out.println("Recebido objecto diferente de String " + 
                    packet.getAddress().getHostAddress() + ":" + packet.getPort());
            return null;
        }

        System.out.println("Recebido \"" + request + "\" de " + 
                packet.getAddress().getHostAddress() + ":" + packet.getPort());
        
        return request;
    
    }
    
    public boolean serverExists(String nome){   
        for (Server_Registry sr : activeServers) {
            if(sr.getNome().equalsIgnoreCase(nome))
                return true;
        }
        return false;
    }
    
    public void processRequests()
    {
        String receivedMsg;
        ByteArrayOutputStream bOut;
        ObjectOutputStream out;
         
        if(socket == null){
            return;
        }
        
        while(true){
                
            try{                    

                receivedMsg = waitDatagram();

                if(receivedMsg == null){
                    continue;
                }

                //Dados
                String []comando= receivedMsg.split(" ");
                InetAddress ip;
                int porto;
                String nome;
                                
                switch(comando[0].toUpperCase()){
                    case "SERVIDOR": 
                        nome = comando[1];
                        ip = InetAddress.getByName(comando[2]);
                        porto = Integer.parseInt(comando[3]);
                        
                        if(!serverExists(nome)) {
                            Server_Registry sr = new Server_Registry(nome, ip, porto);
                            activeServers.add(new Server_Registry(nome, ip, porto));
                            ListenerThread mt = new ListenerThread(ip, porto);
                        }
                        
                        break;
                    case "CLIENTE": 
                        
                        break;
                    default: 
                        throw new Exception("Command failure(incorrect size or command)");
                }
                
                bOut = new ByteArrayOutputStream(MAX_SIZE);            
                out = new ObjectOutputStream(bOut);

                packet.setData(bOut.toByteArray());
                packet.setLength(bOut.size());

                System.out.println("Tamanho da resposta serializada: "+bOut.size());
                //O ip e porto de destino já se encontram definidos em packet
                socket.send(packet);
                
            }catch(IOException e){
                System.out.println(e);
            }catch(NumberFormatException e){
                System.out.println(e);
            }catch(Exception e){
                System.out.println(e);
            }
        }
            
        
    }
    
    public void closeSocket()
    {
        if(socket != null){
            socket.close();
        }
    }
    
    public static void main(String[] args) {
        if(args[0] == null) {
            System.out.println("Syntax error: ServicoDirectoria <listeningPort>");
            return;
        }
        
        try {
            DirectoryService sd = new DirectoryService(Integer.parseInt(args[0]));
            sd.processRequests();
        } catch(SocketException e) {
            e.printStackTrace();
        }
    }
    
}
