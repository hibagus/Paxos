// (C) 2020 Qinzhe Wu (qw2699), Bagus Hanindhito (bh29293)

package kvpaxos;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * This is a subset of entire test cases
 * For your reference only.
 */
public class KVPaxosTest {


    public void check(Client ck, String key, Integer value){
        Integer v = ck.Get(key);
        assertTrue("Get(" + key + ")->" + v + ", expected " + value, v.equals(value));
    }

    public void check_1_of_n(Client ck, String key, Integer[] values){
        Integer v = ck.Get(key);
        boolean match = false;
        String values_str = "";
        for(Integer val : values){
            if(v.equals(val)){
                match = true;
            }
            values_str += val + ",";
        }
        assertTrue("Get(" + key + ")->" + v + ", expected one of " + values_str,
                   match);
    }

    private void cleanup(Server[] servers){
        for(int i = 0; i < servers.length; i++){
            if(servers[i] != null){
                servers[i].Kill();
            }
        }
    }


    @Test
    public void TestBasic(){
        final int npaxos = 5;
        String host = "127.0.0.1";
        String[] peers = new String[npaxos];
        int[] ports = new int[npaxos];

        Server[] kva = new Server[npaxos];
        for(int i = 0 ; i < npaxos; i++){
            ports[i] = 1100+i;
            peers[i] = host;
        }
        for(int i = 0; i < npaxos; i++){
            kva[i] = new Server(peers, ports, i);
        }

        Client ck = new Client(peers, ports);
        System.out.println("Test: Basic put/get ...");
        ck.Put("app", 6);
        check(ck, "app", 6);
        ck.Put("a", 70);
        check(ck, "a", 70);

        System.out.println("... Passed");
        cleanup(kva);

    }

    @Test
    public void TestMultipleClients(){
        final int npaxos = 5;
        String host = "127.0.0.1";
        String[] peers = new String[npaxos];
        int[] ports = new int[npaxos];

        Server[] kva = new Server[npaxos];
        for(int i = 0 ; i < npaxos; i++){
            ports[i] = 1100+i;
            peers[i] = host;
        }
        for(int i = 0; i < npaxos; i++){
            kva[i] = new Server(peers, ports, i);
        }

        Client ck0 = new Client(peers, ports);
        Client ck1 = new Client(peers, ports);
        System.out.println("Test: Multiple clients put/get ...");
        ck0.Put("app", 6);
        check(ck1, "app", 6);
        ck1.Put("a", 70);
        check(ck0, "a", 70);
        ck0.Put("ck0", 0);
        ck1.Put("ck1", 1);
        check(ck1, "ck0", 0);
        check(ck0, "ck1", 1);
        ck0.Put("sameKeySameVal", 100);
        ck1.Put("sameKeySameVal", 100);
        check(ck1, "sameKeySameVal", 100);
        check(ck0, "sameKeySameVal", 100);
        ck0.Put("sameKeyDiffVal", 200);
        ck1.Put("sameKeyDiffVal", 300);
        check_1_of_n(ck0, "sameKeyDiffVal", new Integer[]{200, 300});

        System.out.println("... Passed");
        cleanup(kva);

    }

    @Test
    public void TestDuplicated(){
        final int npaxos = 5;
        String host = "127.0.0.1";
        String[] peers = new String[npaxos];
        int[] ports = new int[npaxos];

        Server[] kva = new Server[npaxos];
        for(int i = 0 ; i < npaxos; i++){
            ports[i] = 1100+i;
            peers[i] = host;
        }
        for(int i = 0; i < npaxos; i++){
            kva[i] = new Server(peers, ports, i);
        }

        Client ck = new Client(peers, ports);
        System.out.println("Test: Duplicated put/get ...");
        ck.Put("app", 6);
        check(ck, "app", 6);
        ck.seq = 0;
        ck.Put("app", 6);
        check(ck, "app", 6);
        ck.Put("a", 70);
        check(ck, "a", 70);
        ck.seq = 2;
        ck.Put("a", 70);
        check(ck, "a", 70);

        System.out.println("... Passed");
        cleanup(kva);

    }

}
