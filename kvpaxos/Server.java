// (C) 2020 Qinzhe Wu (qw2699), Bagus Hanindhito (bh29293)

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
    private Map<Integer, Integer> _clientSeqs;


    public Server(String[] servers, int[] ports, int me){
        this.me = me;
        this.servers = servers;
        this.ports = ports;
        this.mutex = new ReentrantLock();
        this.px = new Paxos(me, servers, ports);
        // Your initialization code here
        this.seq = 0;
        this._kvs = new HashMap<String, Integer>();
        this._clientSeqs = new HashMap<Integer, Integer>();



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

    public void Kill(){
        px.Kill();
    }


    // RMI handlers
    public Response Get(Request req){
        // Your code here
        mutex.lock();
        try{
            while(true){
                // For multiple clients, get a key from req instead
                if(_clientSeqs.containsKey(req.clientId) &&
                   req.op.ClientSeq <= _clientSeqs.get(req.clientId)){
                    return new Response(_kvs.get(req.op.key));
                    //Assume client only retry if it did not get the value
                    //so even this time server responses a different value
                    //it doesn't matter
                }
                px.Start(seq, req.op);
                Op op = wait(seq);
                px.Done(seq);
                seq++;
                if(op.equals(req.op)){
                    _clientSeqs.put(req.clientId, op.ClientSeq);
                    return new Response(_kvs.get(req.op.key));
                } else{
                    if(null != op){
                        _clientSeqs.put(req.clientId, op.ClientSeq);
                        if("Put" == op.op){
                            _kvs.put(op.key, op.value);
                        }
                    }
                }
                px.Min(); // will actually release
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
                // For multiple clients, get a key from req instead
                if(_clientSeqs.containsKey(req.clientId) &&
                   req.op.ClientSeq <= _clientSeqs.get(req.clientId)){
                    return new Response(req.op.value);
                    // so client knows it's done
                }
                px.Start(seq, req.op);
                Op op = wait(seq);
                px.Done(seq);
                seq++;
                if(op.equals(req.op)){
                    _clientSeqs.put(req.clientId, op.ClientSeq);
                    _kvs.put(op.key, op.value);
                    return new Response(op.value);
                } else{
                    if(null != op){
                        _clientSeqs.put(req.clientId, op.ClientSeq);
                        if("Put" == op.op){
                            _kvs.put(op.key, op.value);
                        }
                    }
                }
                px.Min(); // will actually release
            }
        } catch(Exception e){
            e.printStackTrace();
        } finally{
            mutex.unlock();
        }
        return null;
    }


}
