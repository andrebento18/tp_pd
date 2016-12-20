
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.List;


/**
 *
 * @author andre
 */
public class Chat {
    
    private DatagramSocket socket;
    private DatagramPacket packet;
    private List<ClientInfo> activeClients;
    
    public Chat(List<ClientInfo> activeClients) {
        this.socket = null;
        this.packet = null;
        this.activeClients = activeClients;
    }
    
    private void prepareChatMSG(MSG msg) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(Constants.MAX_SIZE);
        try{
            ObjectOutputStream os = new ObjectOutputStream(new
                                    BufferedOutputStream(byteStream));
            os.flush();
            os.writeObject((MSG)msg);
            os.flush();

            byte[] sendBuf = byteStream.toByteArray();
        
            packet = new DatagramPacket(sendBuf, sendBuf.length);
        } catch(IOException ex) {
            ex.printStackTrace();
        }
        System.out.println("Reply MSG sended!");
    }
    
    public void sendChatMSGToAll(MSG msg) {
        prepareChatMSG(msg);
        try {
            for(ClientInfo ci : activeClients) {
                packet.setAddress(ci.getClientAddress());
                socket = new DatagramSocket();
                socket.send(packet);
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
    
    /* FALTA IMPLEMENTAR ISTO (NÃO TIVE TEMPO)
    public void sendChatMSGToDesignatedClients(MSG msg, String clients) {
        clients.trim();
        String[] clientsUsernames = clients.split(",");
        for(ClientInfo ci : activeClients) {
            if(ci.equals(new Client(clientsUsernames)){
            
            }
        }
    }
    */
}
