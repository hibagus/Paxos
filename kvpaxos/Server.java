package kvpaxos;
import paxos.Paxos;
import paxos.State;
// You are allowed to call Paxos.Status to check if agreement was made.

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.locks.ReentrantLock;

import java.util.HashMap;
import java.util.Map;

public class Server implements KVPaxosRMI {

    ReentrantLock mutex;
    Registry registry;
    Paxos px;
    int me;

    String[] servers;
    int[] ports;
    KVPaxosRMI stub;

    // Your definitions here
    int seq;
    private Map<String, Integer> _kvs;


    public Server(String[] servers, int[] ports, int me){
        this.me = me;
        this.servers = servers;
        this.ports = ports;
        this.mutex = new ReentrantLock();
        this.px = new Paxos(me, servers, ports);
        // Your initialization code here
        this.seq = 0;
        this._kvs = new HashMap<String, Integer>();



        try{
            System.setProperty("java.rmi.server.hostname", this.servers[this.me]);
            registry = LocateRegistry.getRegistry(this.ports[this.me]);
            stub = (KVPaxosRMI) UnicastRemoteObject.exportObject(this, this.ports[this.me]);
            registry.rebind("KVPaxos", stub);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    // Helper function
    public Op wait(int seq){
        int to = 10;
        while(true){
            Paxos.retStatus ret = this.px.Status(seq);
            if(State.Decided == ret.state){
                return Op.class.cast(ret.v);
            }
            try{
                Thread.sleep(to);
            } catch(Exception e){
                e.printStackTrace();
            }
            if(1000 > to){
                to = to * 2;
            }
        }
    }


    // RMI handlers
    public Response Get(Request req){
        // Your code here
        mutex.lock();
        try{
            while(true){
                px.Start(seq, req.op);
                Op op = wait(seq);
                seq++;
                if(op.equals(req.op)){
                    return new Response(_kvs.get(req.op.key));
                } else{
                    if("Put" == op.op){
                        _kvs.put(op.key, op.value);
                    }
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        } finally{
            mutex.unlock();
        }
        return null;
    }

    public Response Put(Request req){
        // Your code here
        mutex.lock();
        try{
            while(true){
                px.Start(seq, req.op);
                Op op = wait(seq);
                seq++;
                if(op.equals(req.op)){
                    _kvs.put(op.key, op.value);
                    return new Response(op.value);
                } else{
                    if("Put" == op.op){
                        _kvs.put(op.key, op.value);
                    }
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        } finally{
            mutex.unlock();
        }
        return null;
    }


}
