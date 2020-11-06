package paxos;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.Registry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.Set;

/**
 * This class is the main class you need to implement paxos instances.
 */
public class Paxos implements PaxosRMI, Runnable{

    ReentrantLock mutex;
    String[] peers; // hostname
    int[] ports; // host port
    int me; // index into peers[]

    Registry registry;
    PaxosRMI stub;

    AtomicBoolean dead;// for testing
    AtomicBoolean unreliable;// for testing

    // Your data here
    private class PropNumGenerator
    {
        // This class is used to generate unique, monotonic-increasing, proposal number.
        // Each Paxos Peer will have their unique identifier, and thus we use them to generate unique proposalNumber.
        // Threadsafe

        private int _paxosPeerID;
        private int _paxosNumPeer;
        private int _currentMultiplier;
        private int _currentPropNum;

        public PropNumGenerator(int paxosPeerID, int paxosNumPeer)
        {
            this._paxosPeerID  = paxosPeerID;
            this._paxosNumPeer = paxosNumPeer;
            this._currentMultiplier = 0;
            this._currentPropNum = paxosPeerID; // must be unique
        }

        public int getCurrentPropNum() {return this._currentPropNum;}
        public synchronized int getNextPropNum() // Threadsafe
        {
            _currentMultiplier = _currentMultiplier + 1;
            this._currentPropNum = this._currentMultiplier * this._paxosNumPeer + this._paxosPeerID;
            return this._currentPropNum;
        }
    }

    private class PaxosAgreementInstance
    {
        // This class hold information about each agreement instances.
        // A new instance of PaxosAgreementInstance will be created when calling Start().
        // Each instance will run on its own thread and thus multiple instances can run concurrently.
        private State _agreementstate;
        private int _highestPropNumResponded;
        private int _highestPropNumAccepted;
        private Object _propValwithHighestPropNumAccepted;
        private Object _propValAssigned;
        private ReentrantLock _mutex;

        // Constructor
        public PaxosAgreementInstance(Object propValAssigned)
        {
            this._agreementstate = State.Pending;
            this._highestPropNumResponded = -1;
            this._highestPropNumAccepted = -1;
            this._propValwithHighestPropNumAccepted = null;
            this._propValAssigned = propValAssigned;
            this._mutex = new ReentrantLock();
        }

        // Getter
        public State getAgreementState() {return this._agreementstate;}
        public int getHighestPropNumResponded() {return this._highestPropNumResponded;}
        public int getHighestPropNumAccepted() {return this._highestPropNumResponded;}
        public Object getPropValwithHighestPropNumAccepted() {return this._propValwithHighestPropNumAccepted;}
        public Object getPropValAssigned() {return this._propValAssigned;}

        // Setter
        public void setAgreementState(State agreementstate) {this._agreementstate = agreementstate;}
        public void setHighestPropNumResponded(int highestPropNumResponded) {this._highestPropNumResponded = highestPropNumResponded;}
        public void setHighestPropNumAccepted(int highestPropNumAccepted) {this._highestPropNumAccepted = highestPropNumAccepted;}
        public void setpropValwithHighestPropNumAccepted(Object propValwithHighestPropNumAccepted) {this._propValwithHighestPropNumAccepted = propValwithHighestPropNumAccepted;}
        public void setPropValAssigned(Object propValAssigned) {this._propValAssigned = propValAssigned;}

        // Other Public Method
        public void advanceAgreementState()
        {
            switch(this._agreementstate)
            {
                case Pending   : {this._agreementstate = State.Decided; break;}
                case Decided   : {this._agreementstate = State.Forgotten; break;}
                case Forgotten : {this._agreementstate = State.Forgotten; break;}
                default        : {this._agreementstate = this._agreementstate; break;}
            }
        }

        public void lock() {this._mutex.lock();}
        public void unlock() {this._mutex.unlock();}
    }

    private boolean isMajorityofResponsesOkay(Response[] PaxosPeerResponses)
    {
        int majorityNumber = (PaxosPeerResponses.length / 2) + 1; // should I handle even or odd case?
        int numofOKResponses = 0;
        for (Response PaxosPeerResponse : PaxosPeerResponses)
        {
            if (PaxosPeerResponse != null && PaxosPeerResponse.getStatus())
            {
                numofOKResponses = numofOKResponses + 1;
            }
        }

        boolean result = false;
        if(numofOKResponses >= majorityNumber) {result = true;}
        return result;
    }

    private Object findPropValwithHighestPropNum(Response[] PaxosPeerResponses, Object propValAssigned)
    {
        Object PropValwithHighestPropNum = null;
        // Find the highest proposal number

        int HighestPropNum = -1;
        for (Response PaxosPeerResponse : PaxosPeerResponses)
        {
            if (PaxosPeerResponse != null && PaxosPeerResponse.getStatus())
            {
                if(PaxosPeerResponse.getPropNum() > HighestPropNum)
                {
                    HighestPropNum = PaxosPeerResponse.getPropNum();
                    PropValwithHighestPropNum = PaxosPeerResponse.getPropVal();
                }
            }
        }

        if(PropValwithHighestPropNum == null)
        {
            PropValwithHighestPropNum = propValAssigned;
        }
        return PropValwithHighestPropNum;
    }

    private void removePaxosAgreementInstances(int minSeqNum)
    {
        for (int seqNum : Set.copyOf(this._paxosAgreementInstances.keySet()))
        {
            if (seqNum < minSeqNum)
            {
                this._paxosAgreementInstances.remove(seqNum);
            }
        }
    }

    // This is used to generate unique proposal number
    private PropNumGenerator _propNumGenerator;

    // This is used to store all outstanding Paxos Agreement Instances.
    // The mapping is done between sequenceNumber (Integer) to each PaxosAgreementInstance.
    private Map<Integer, PaxosAgreementInstance> _paxosAgreementInstances;

    // This is used to store all outstanding Paxos Agreement Threads.
    // The mapping is done between Thread ID (Integer) and sequenceNumber (Integer).
    // Thread ID is always unique for all outstanding threads in a process.
    // Thread ID can be reused if previous thread has been terminated.
    private Map<Long, Integer> _paxosAgreementThreads;

    // This array is used to store the maximum sequence number for this peer.
    private int _maxSeq;

    // This array is used to store the maximum sequence number that have been seen from each Paxos Peers.
    private int[] _maxSeqNumfromAllPaxosPeers;

    /**
     * Call the constructor to create a Paxos peer.
     * The hostnames of all the Paxos peers (including this one)
     * are in peers[]. The ports are in ports[].
     */
    public Paxos(int me, String[] peers, int[] ports){

        this.me = me;
        this.peers = peers;
        this.ports = ports;
        this.mutex = new ReentrantLock();
        this.dead = new AtomicBoolean(false);
        this.unreliable = new AtomicBoolean(false);

        // Your initialization code here
        this._propNumGenerator = new PropNumGenerator(me, peers.length);
        this._paxosAgreementInstances = new HashMap<Integer, PaxosAgreementInstance>();
        this._paxosAgreementThreads = new HashMap<Long, Integer>();

        this._maxSeq = -1;
        this._maxSeqNumfromAllPaxosPeers = new int[peers.length];
        Arrays.fill(this._maxSeqNumfromAllPaxosPeers, -1);

        // register peers, do not modify this part
        try{
            System.setProperty("java.rmi.server.hostname", this.peers[this.me]);
            registry = LocateRegistry.createRegistry(this.ports[this.me]);
            stub = (PaxosRMI) UnicastRemoteObject.exportObject(this, this.ports[this.me]);
            registry.rebind("Paxos", stub);
        } catch(Exception e){
            e.printStackTrace();
        }
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

        PaxosRMI stub;
        try{
            Registry registry=LocateRegistry.getRegistry(this.ports[id]);
            stub=(PaxosRMI) registry.lookup("Paxos");
            if(rmi.equals("Prepare"))
                callReply = stub.Prepare(req);
            else if(rmi.equals("Accept"))
                callReply = stub.Accept(req);
            else if(rmi.equals("Decide"))
                callReply = stub.Decide(req);
            else
                System.out.println("Wrong parameters!");
        } catch(Exception e){
            return null;
        }
        return callReply;
    }


    /**
     * The application wants Paxos to start agreement on instance seq,
     * with proposed value v. Start() should start a new thread to run
     * Paxos on instance seq. Multiple instances can be run concurrently.
     *
     * Hint: You may start a thread using the runnable interface of
     * Paxos object. One Paxos object may have multiple instances, each
     * instance corresponds to one proposed value/command. Java does not
     * support passing arguments to a thread, so you may reset seq and v
     * in Paxos object before starting a new thread. There is one issue
     * that variable may change before the new thread actually reads it.
     * Test won't fail in this case.
     *
     * Start() just starts a new thread to initialize the agreement.
     * The application will call Status() to find out if/when agreement
     * is reached.
     */
    public void Start(int seq, Object value){
        // Your code here
        // If Start() is called with a sequence number less than Min(), the Start() call should be ignored
        // Start() will begin proposer function in a dedicated thread.

        // TODO: Handle existing sequence with different value

        // Check whether seq is less than Min().
        // if yes, then Start() should exit prematurely.
        if(seq < Min()) {return;}

        // acquire lock
        this.mutex.lock();

        // set maximum sequence number seen by this peer
        this._maxSeq = Math.max(seq, this._maxSeq);

        // Create new paxos Agreement Thread in which Agreement Instance will run
        Thread paxosAgreementThread = new Thread(this);
        this._paxosAgreementThreads.put(paxosAgreementThread.getId(), seq);

        PaxosAgreementInstance paxosAgreementInstance;

        // Create new paxos Agreement Instance in which Agreement will be done
        if(!this._paxosAgreementInstances.containsKey(seq))
        {
            paxosAgreementInstance = new PaxosAgreementInstance(value);
            this._paxosAgreementInstances.put(seq,paxosAgreementInstance);
        }
        else
        {
            paxosAgreementInstance = this._paxosAgreementInstances.get(seq);
            paxosAgreementInstance.lock();
            paxosAgreementInstance.setPropValAssigned(value);
            paxosAgreementInstance.unlock();
        }

        // Start Agreement Thread
        paxosAgreementThread.start();

        // release lock
        this.mutex.unlock();
    }

    @Override
    public void run(){
        //Your code here
        //This is code for proposer, will run in infinite loop until decided.
        Thread paxosAgreementThread = Thread.currentThread();
        this.mutex.lock();
        int seqNum = this._paxosAgreementThreads.get(paxosAgreementThread.getId());
        PaxosAgreementInstance paxosAgreementInstance = this._paxosAgreementInstances.get(seqNum);
        this.mutex.unlock();

        // Run Agreement Loop until Decided
        while(paxosAgreementInstance.getAgreementState() != State.Decided)
        {
            Response[] PaxosPeerResponses = new Response[this.peers.length];

            // Generate Proposal Number
            int propNum = this._propNumGenerator.getNextPropNum();

            // Send Prepare Request to All Peers
            for(int peerID = 0; peerID < this.peers.length; peerID++)
            {
                if(peerID == this.me) // call to itself without RMI
                {
                    PaxosPeerResponses[peerID] = Prepare(new Request(propNum, null, seqNum));
                }
                else
                {
                    PaxosPeerResponses[peerID] = Call("Prepare", new Request(propNum, null, seqNum), peerID);
                }
            }

            // Check the majority response of the acceptors. If majorities say okay, then proceed to the next step.
            // Otherwise, restart the agreement by generating higher proposal number.
            if(!isMajorityofResponsesOkay(PaxosPeerResponses))
            {
                continue;
            }

            // Find the Highest Proposal Value to propose for current propNum
            Object PropValwithHighestPropNum = findPropValwithHighestPropNum(PaxosPeerResponses, paxosAgreementInstance.getPropValAssigned());

            // Send Accept Request to All Peers
            for(int peerID = 0; peerID < this.peers.length; peerID++)
            {
                if(peerID == this.me) // call to itself without RMI
                {
                    PaxosPeerResponses[peerID] = Accept(new Request(propNum, PropValwithHighestPropNum, seqNum));
                }
                else
                {
                    PaxosPeerResponses[peerID] = Call("Accept", new Request(propNum, PropValwithHighestPropNum, seqNum), peerID);
                }
            }

            // Check the majority response of the acceptors. If majorities say okay, then proceed to the next step.
            // Otherwise, restart the agreement by generating higher proposal number.
            if(!isMajorityofResponsesOkay(PaxosPeerResponses))
            {
                continue;
            }

            // Send Decide Request to All Peers
            for(int peerID = 0; peerID < this.peers.length; peerID++)
            {
                if(peerID == this.me) // call to itself without RMI
                {
                    PaxosPeerResponses[peerID] = Decide(new Request(propNum, PropValwithHighestPropNum, seqNum));
                }
                else
                {
                    PaxosPeerResponses[peerID] = Call("Decide", new Request(propNum, PropValwithHighestPropNum, seqNum), peerID);
                }

                // Since the _maxSeqNumfromAllPaxosPeers is piggybacked on Decide Response, we can update this array.
                if(PaxosPeerResponses[peerID] != null)
                {
                    this._maxSeqNumfromAllPaxosPeers[peerID] = PaxosPeerResponses[peerID].getMaxSeqNum();
                }
            }

        }
    }

    // RMI handler
    public Response Prepare(Request req){
        // your code here
        // acquire lock
        this.mutex.lock();

        // Create new paxos Agreement Instance in which Agreement will be done
        PaxosAgreementInstance paxosAgreementInstance;
        if(!this._paxosAgreementInstances.containsKey(req.getSeqNum()))
        {
            paxosAgreementInstance = new PaxosAgreementInstance(null);
            this._paxosAgreementInstances.put(req.getSeqNum(),paxosAgreementInstance);
        }
        else
        {
            paxosAgreementInstance = this._paxosAgreementInstances.get(req.getSeqNum());
        }

        boolean ResponseStatus = false;

        paxosAgreementInstance.lock();
        // Response OK only when proposal number from the request is higher than it has ever responded.
        if(req.getPropNum() >= paxosAgreementInstance.getHighestPropNumResponded())
        {
            paxosAgreementInstance.setHighestPropNumResponded(req.getPropNum());
            ResponseStatus = true;
        }
        Response resp = new Response(ResponseStatus, paxosAgreementInstance.getHighestPropNumAccepted(), paxosAgreementInstance.getPropValwithHighestPropNumAccepted(), req.getSeqNum(), -1);
        // release lock
        paxosAgreementInstance.unlock();
        this.mutex.unlock();
        return resp;
    }

    public Response Accept(Request req){
        // your code here
        this.mutex.lock();

        // Create new paxos Agreement Instance in which Agreement will be done
        PaxosAgreementInstance paxosAgreementInstance;
        if(!this._paxosAgreementInstances.containsKey(req.getSeqNum()))
        {
            paxosAgreementInstance = new PaxosAgreementInstance(null);
            this._paxosAgreementInstances.put(req.getSeqNum(),paxosAgreementInstance);
        }
        else
        {
            paxosAgreementInstance = this._paxosAgreementInstances.get(req.getSeqNum());
        }

        boolean ResponseStatus = false;

        paxosAgreementInstance.lock();
        // Response OK only when proposal number from the request is higher than it has ever responded.
        if(req.getPropNum() >= paxosAgreementInstance.getHighestPropNumResponded())
        {
            paxosAgreementInstance.setHighestPropNumAccepted(req.getPropNum());
            paxosAgreementInstance.setpropValwithHighestPropNumAccepted(req.getPropVal());
            ResponseStatus = true;
        }
        // release lock
        paxosAgreementInstance.unlock();
        this.mutex.unlock();
        return new Response(ResponseStatus, -1, null, -1, -1);
    }

    public Response Decide(Request req){
        // your code here
        this.mutex.lock();

        // Create new paxos Agreement Instance in which Agreement will be done
        PaxosAgreementInstance paxosAgreementInstance;
        if(!this._paxosAgreementInstances.containsKey(req.getSeqNum()))
        {
            paxosAgreementInstance = new PaxosAgreementInstance(null);
            this._paxosAgreementInstances.put(req.getSeqNum(),paxosAgreementInstance);
        }
        else
        {
            paxosAgreementInstance = this._paxosAgreementInstances.get(req.getSeqNum());
        }

        boolean ResponseStatus = false;

        paxosAgreementInstance.lock();
        // Response OK only when proposal number from the request is higher than it has ever responded.
        if(paxosAgreementInstance.getAgreementState() != State.Decided)
        {
            paxosAgreementInstance.setAgreementState(State.Decided);
            paxosAgreementInstance.setpropValwithHighestPropNumAccepted(req.getPropVal());
            ResponseStatus = true;
        }
        // release lock
        paxosAgreementInstance.unlock();
        this.mutex.unlock();
        return new Response(ResponseStatus, -1, null, -1, this._maxSeqNumfromAllPaxosPeers[this.me]);
    }

    /**
     * The application on this machine is done with
     * all instances <= seq.
     *
     * see the comments for Min() for more explanation.
     */
    public void Done(int seq) {
        // Your code here
        this.mutex.lock();
        this._maxSeqNumfromAllPaxosPeers[this.me] = seq;
        this.mutex.unlock();
    }


    /**
     * The application wants to know the
     * highest instance sequence known to
     * this peer.
     */
    public int Max(){
        // Your code here
        return this._maxSeq;
    }

    /**
     * Min() should return one more than the minimum among z_i,
     * where z_i is the highest number ever passed
     * to Done() on peer i. A peers z_i is -1 if it has
     * never called Done().

     * Paxos is required to have forgotten all information
     * about any instances it knows that are < Min().
     * The point is to free up memory in long-running
     * Paxos-based servers.

     * Paxos peers need to exchange their highest Done()
     * arguments in order to implement Min(). These
     * exchanges can be piggybacked on ordinary Paxos
     * agreement protocol messages, so it is OK if one
     * peers Min does not reflect another Peers Done()
     * until after the next instance is agreed to.

     * The fact that Min() is defined as a minimum over
     * all Paxos peers means that Min() cannot increase until
     * all peers have been heard from. So if a peer is dead
     * or unreachable, other peers Min()s will not increase
     * even if all reachable peers call Done. The reason for
     * this is that when the unreachable peer comes back to
     * life, it will need to catch up on instances that it
     * missed -- the other peers therefore cannot forget these
     * instances.
     */
    public int Min(){
        // Your code here
        mutex.lock();
        int minSeq = Arrays.stream(this._maxSeqNumfromAllPaxosPeers).min().getAsInt() + 1;
        removePaxosAgreementInstances(minSeq);
        mutex.unlock();
        return minSeq;
    }



    /**
     * the application wants to know whether this
     * peer thinks an instance has been decided,
     * and if so what the agreed value is. Status()
     * should just inspect the local peer state;
     * it should not contact other Paxos peers.
     */
    public retStatus Status(int seq){
        // Your code here
        // If Status() is called with a sequence number less than Min(), Status()
        // should return forgotten.

        State state;
        Object val;
        if(seq < Min())
        {
            state = State.Forgotten;
            val = null;
        }
        else
        {
            this.mutex.lock();
            PaxosAgreementInstance paxosAgreementInstance;
            if(!this._paxosAgreementInstances.containsKey(seq))
            {
                paxosAgreementInstance = new PaxosAgreementInstance(null);
                this._paxosAgreementInstances.put(seq,paxosAgreementInstance);
            }
            else
            {
                paxosAgreementInstance = this._paxosAgreementInstances.get(seq);
            }
            state = paxosAgreementInstance.getAgreementState();
            val = paxosAgreementInstance.getPropValwithHighestPropNumAccepted();
            this.mutex.unlock();
        }
        return new retStatus(state, val);
    }

    /**
     * helper class for Status() return
     */
    public class retStatus{
        public State state;
        public Object v;

        public retStatus(State state, Object v){
            this.state = state;
            this.v = v;
        }
    }

    /**
     * Tell the peer to shut itself down.
     * For testing.
     * Please don't change these four functions.
     */
    public void Kill(){
        this.dead.getAndSet(true);
        if(this.registry != null){
            try {
                UnicastRemoteObject.unexportObject(this.registry, true);
            } catch(Exception e){
                System.out.println("None reference");
            }
        }
    }

    public boolean isDead(){
        return this.dead.get();
    }

    public void setUnreliable(){
        this.unreliable.getAndSet(true);
    }

    public boolean isunreliable(){
        return this.unreliable.get();
    }


}
