// (C) 2020 Qinzhe Wu (qw2699), Bagus Hanindhito (bh29293)

package kvpaxos;
import java.io.Serializable;

/**
 * Please fill in the data structure you use to represent the request message for each RMI call.
 * Hint: Make it more generic such that you can use it for each RMI call.
 * Hint: Easier to make each variable public
 */
public class Request implements Serializable {
    static final long serialVersionUID=11L;
    // Your data here
    public int clientId;
    public Op op;

    // Your constructor and methods here
    public Request(int clientId, Op op){
        this.clientId = clientId;
        this.op = op;
    }

}
