import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Client {    
    private DatagramSocket udpSocket;
    private DatagramPacket packet; //para receber os pedidos e enviar as respostas
    private ObjectInputStream oIn;
    private ObjectOutputStream oOut;
    private InetAddress directoryServiceIp;
    private int directoryServicePort;
    private String line;
    private String username;
    private ChatThreadReceive chatThread;
    private HeartbeatThreadSend heartBeatThread;
    private DistributedFileSystem fileSystem;
    private Map<String, ServerConnection> serverList;
    private List<ClientInfo> clientList;
    private ServerConnection currentConnection;
    
    public Client(InetAddress directoryServiceIp, int directoryServicePort) throws SocketException{
        udpSocket = new DatagramSocket();
        packet = null;
        this.directoryServiceIp = directoryServiceIp;
        this.directoryServicePort = directoryServicePort;
        serverList = new HashMap<>();
        clientList = new ArrayList<>();
        fileSystem = new DistributedFileSystem(this);
        username = null;
        currentConnection = null;
    }
    
    public InetAddress getDirectoryServiceIp() { 
        return directoryServiceIp; 
    }

    public ServerConnection getCurrentConnection() {
        return currentConnection;
    }
    
    public void setCurrentConnection(ServerConnection currentConnection) {
        this.currentConnection = currentConnection;
    }
    
    public void setDirectoryServiceIp(InetAddress directoryServiceIp) { 
        this.directoryServiceIp = directoryServiceIp; 
    }

    public int getDirectoryServicePort() { 
        return directoryServicePort; 
    }

    public void setDirectoryServicePort(int directoryServicePort) { 
        this.directoryServicePort = directoryServicePort; 
    }
    
    private void fillMsg(String line){
        String [] lineSplit = line.split(" ");
        msg.getCMD().addAll(Arrays.asList(lineSplit));
    }
    
    public void sendRequestUdp(String cmd) throws IOException{
        msg = new MSG();
        fillMsg(Constants.CLIENT+" "+cmd);
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        oOut = new ObjectOutputStream(bOut);
        oOut.writeObject(msg);
        oOut.flush();
        packet = new DatagramPacket(bOut.toByteArray(), bOut.toByteArray().length, directoryServiceIp, directoryServicePort);
        udpSocket.send(packet);
    }
    
    public Object receiveResponseUdp() throws IOException, ClassNotFoundException{
        packet = new DatagramPacket(new byte[Constants.MAX_SIZE], Constants.MAX_SIZE);
        udpSocket.receive(packet);
        oIn = new ObjectInputStream(new ByteArrayInputStream(packet.getData(), 0, packet.getLength()));

        return oIn.readObject();
    }
    
    public void sendRequestTcp(String cmd) throws IOException{
        msg = new MSG();
        fillMsg(Constants.CLIENT+" "+cmd);
        oOut = new ObjectOutputStream(currentConnection.getSocket().getOutputStream());
        oOut.writeObject(msg);
        oOut.flush();
    }
    
    public Object receiveResponseTcp() throws IOException, ClassNotFoundException{
        oIn = new ObjectInputStream(currentConnection.getSocket().getInputStream());
        
        return oIn.readObject();
    }
    
    public ServerConnection getServerConnection(String serverName){
        return serverList.get(serverName);
    }
    
    public boolean checkIfImConnected(String serverName){
        return getServerConnection(serverName) == null ? 
                false : getServerConnection(serverName).isConnected();
    }
    
    public void closeUdpSocket(){
        if(udpSocket != null)
            udpSocket.close();
    }
    
    public void closeTcpSocket() throws IOException{
        for (Map.Entry<String, ServerConnection> entry : serverList.entrySet()) {
            ServerConnection value = entry.getValue();
            if(value.getSocket() != null)
                value.getSocket().close();
        }
    }

    private void updateClientList(List<ClientInfo> list){ 
        clientList.clear();
        clientList = list; 
        System.out.println("Client List Updated\n---------------------------------------");
        System.out.print(listClients());
        
    }
    
    private String listClients(){
        String list = "";
        for (ClientInfo c : clientList) {
            list += c.getUsername() + "\n";
        }
        
        list += "---------------------------------------\n";
        return list;
    } 
    
    private void updateServerList(List<ServerInfo> list){
        for (ServerInfo item : list) {
            if(!serverList.containsKey(item.getName())){
                serverList.put(item.getName(), new ServerConnection(item, null));
            }
        }
        
        for (Map.Entry<String, ServerConnection> entry : serverList.entrySet()) {
            String serverName = entry.getKey();
            ServerConnection server = entry.getValue();
            if(!list.contains(server.getServerInfo())){
                if(serverList.get(serverName).getSocket() != null){
                    try {
                        serverList.get(serverName).getSocket().close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
                serverList.remove(serverName, server);
            }
        }
        
        System.out.println("\tServer List Updated\n---------------------------------------\n");
        System.out.print(listServers());
    }
    
    private String listServers(){
        String list = null;
        list = ("Server Name \tState (Connected/Not Connected\n");
        for (Map.Entry<String, ServerConnection> entry : serverList.entrySet()) {
            String serverName = entry.getKey();
            ServerConnection serverL = entry.getValue();
            list += (serverName);
            if(serverL.getSocket() == null) list += ("\tNot Connected\n");
            else list += ("\tConnected\n");
            
        }
        list += "\n---------------------------------------\n";
        return list;
    } 
        
    private int processClientCommand(String line){
        String [] lineSplit = line.split(" ");
        String cmd = lineSplit[0].trim();
        
        if(lineSplit.length <= 2){
            if(cmd.equalsIgnoreCase(Constants.CMD_CONNECT)){
                String serverName = lineSplit[1].trim();
                
            }else
                return Constants.CODE_CMD_NOT_RECOGNIZED;
        }
        
        return Constants.CODE_CMD_FAILURE;
    }
    
    private void processServerCommand(MSG msg){
        switch(msg.getMSGCode()){
            case Constants.CODE_CONNECT_OK: 
                System.out.println("Connected to: " + getWhereAmI());
                
                break;
            default: break;
        }
    }
    
    private void processDirectoryServiceCommand(MSG msg){
        switch(msg.getMSGCode()){
            case Constants.CODE_LOGOUT_OK:
                if(heartBeatThread != null)
                    heartBeatThread.terminate();
                if(chatThread != null){
                    chatThread.terminate();
                    chatThread = null;
                }
                System.out.println("You logged out"); 
                break;
            case Constants.CODE_LOGIN_OK:
                heartBeatThread = new HeartbeatThreadSend(directoryServiceIp, 
                        udpSocket.getLocalPort());
                heartBeatThread.start();
                if(chatThread == null){
                    chatThread = new ChatThreadReceive();
                    chatThread.start();
                }
                System.out.println("Logged in"); 
                break;
            case Constants.CODE_REGISTER_OK:  System.out.println("You're now registered"); break;
            case Constants.CODE_CHAT_OK:  System.out.println("Chat ok"); break;
            case Constants.CODE_LIST_OK:
                if(msg.getServersList()!= null){
                    if(!msg.getServersList().isEmpty())
                        updateServerList(msg.getServersList());
                } 
                else if(msg.getClientList()!= null) {
                    if(!msg.getClientList().isEmpty())
                        updateClientList(msg.getClientList());
                }
                break;
            default: break;
        }
    }
    
    private void processError(int code) throws Exceptions.ConnectFailure, 
            Exceptions.ListFailure, Exceptions.CmdFailure, Exceptions.CmdNotRecognized, 
            Exceptions.RegisterFailure, Exceptions.RegisterClientAlreadyExists, 
            Exceptions.NotLoggedIn, Exceptions.AlreadyLoggedIn, Exceptions.LoginFailure, 
            Exceptions.ChatFailure{
        switch(code){
            case Constants.CODE_CONNECT_FAILURE: 
                throw  new Exceptions.ConnectFailure();
            case Constants.CODE_LIST_FAILURE: 
                throw new Exceptions.ListFailure();
            case Constants.CODE_CMD_NOT_RECOGNIZED:  
                throw new Exceptions.CmdNotRecognized();
            case Constants.CODE_REGISTER_FAILURE: 
                throw new Exceptions.RegisterFailure();
            case Constants.CODE_REGISTER_CLIENT_ALREADY_EXISTS: 
                throw new Exceptions.RegisterClientAlreadyExists();
            case Constants.CODE_LOGIN_NOT_LOGGED_IN:
                throw new Exceptions.NotLoggedIn();
            case Constants.CODE_LOGIN_ALREADY_LOGGED:
                throw new Exceptions.AlreadyLoggedIn();
            case Constants.CODE_LOGIN_FAILURE:
                throw new Exceptions.LoginFailure();
            case Constants.CODE_CHAT_FAILURE:
                throw new Exceptions.ChatFailure();
            case Constants.CODE_CMD_FAILURE: 
                throw new Exceptions.CmdFailure();
            default: break;
        }
    }
    
    private void processCommand(String line) throws Exceptions.CmdNotRecognized, 
            Exceptions.CmdFailure{
        String [] commands = line.split(" ");
        
        if(commands.length == 0)
            throw  new Exceptions.CmdFailure();
        
        String cmd1 = commands[0].trim();
        
        switch(cmd1){
            case Constants.CMD_REGISTER:
                if(commands.length == 3) {
                    fileSystem.register(commands[1].trim(), commands[2].trim());
                    break;
                }
                throw new Exceptions.CmdFailure();
            case Constants.CMD_LOGIN:
                if(commands.length == 3) {
                    fileSystem.login(commands[1].trim(), commands[2].trim());
                    break;
                }
                throw new Exceptions.CmdFailure();
            case Constants.CMD_LOGOUT:
                if(commands.length == 1) {
                    fileSystem.logout();
                    break;
                }
                throw new Exceptions.CmdFailure();
            case Constants.CMD_LIST:
                if(commands.length == 2) {
                    fileSystem.list(commands[1].trim().toUpperCase());
                    break;
                }
                throw new Exceptions.CmdFailure();
            case Constants.CMD_CONNECT:
                if(commands.length == 1) {
                    fileSystem.connect(commands[1].trim().toUpperCase());
                    break;
                }
                throw new Exceptions.CmdFailure();
            case Constants.CMD_DISCONNECT:
                if(commands.length == 1) {
                    fileSystem.disconnect();
                    break;
                }
                throw new Exceptions.CmdFailure();
            case Constants.CMD_SWITCH:
                if(commands.length == 2) {
                    fileSystem.switchSystemType(commands[1].trim().toUpperCase());
                    break;
                }
                throw new Exceptions.CmdFailure();
            default: throw new Exceptions.CmdNotRecognized();
        }
    }
    
    public void runClient(){
        while(true){
            try{
                System.out.print(username + "@" + fileSystem.getCurrentPath() + ">> ");
                line = new Scanner(System.in).nextLine();
                processCommand(line);

//                processError(msg.getMSGCode());
                
            } catch(Exception ex) {
                System.out.println("\n"+ex);
            }
        }
        //client.closeUdpSocket();
    }
    
    public static void main(String[] args) {        
             
        if(args.length != 2){
            System.out.println("Number of arguments invalid: java IP PORT");
            return;
        }
        
        try {
            InetAddress clientIp = InetAddress.getByName(args[0]);
            int clientPort = Integer.parseInt(args[1]);
            new Client(clientIp, clientPort).runClient();
            
        } catch (UnknownHostException | SocketException ex) {
            System.out.println(ex);
            ex.printStackTrace();
        }
    }
}
