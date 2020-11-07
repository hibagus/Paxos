package kvpaxos;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;


public class Client {
    String[] servers;
    int[] ports;

    // Your data here
    int seq;


    public Client(String[] servers, int[] ports){
        this.servers = servers;
        this.ports = ports;
        // Your initialization code here
        this.seq = 0;
    }

    /**
     * Call() sends an RMI to the RMI handler on server with
     * arguments rmi name, request message, and server id. It
     * waits for the reply and return a response message if
     * the server responded, and return null if Call() was not
     * be able to contact the server.
     *
     * You should assume that Call() will time out and return
     * null after a while if it doesn't get a reply from the server.
     *
     * Please use Call() to send all RMIs and please don't change
     * this function.
     */
    public Response Call(String rmi, Request req, int id){
        Response callReply = null;
        KVPaxosRMI stub;
        try{
            Registry registry= LocateRegistry.getRegistry(this.ports[id]);
            stub=(KVPaxosRMI) registry.lookup("KVPaxos");
            if(rmi.equals("Get"))
                callReply = stub.Get(req);
            else if(rmi.equals("Put")){
                callReply = stub.Put(req);}
            else
                System.out.println("Wrong parameters!");
        } catch(Exception e){
            return null;
        }
        return callReply;
    }

    // RMI handlers
    public Integer Get(String key){
        // Your code here
        Request req = new Request(new Op("Get", seq, key, null));
        seq++;
        int serverId = 0;
        while(true){
            Response resp = Call("Get", req, serverId);
            if(null != resp){
                return resp.value;
            }
            serverId = (serverId + 1) % servers.length;
        }
    }

    public boolean Put(String key, Integer value){
        // Your code here
        Request req = new Request(new Op("Put", seq, key, value));
        seq++;
        int serverId = 0;
        while(true){
            Response resp = Call("Put", req, serverId);
            if(null != resp && resp.value.equals(value)){
                return true;
            }
            serverId = (serverId + 1) % servers.length;
        }
    }

}
