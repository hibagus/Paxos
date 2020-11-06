package paxos;
import java.io.Serializable;

/**
 * Please fill in the data structure you use to represent the request message for each RMI call.
 * Hint: You may need the sequence number for each paxos instance and also you may need proposal number and value.
 * Hint: Make it more generic such that you can use it for each RMI call.
 * Hint: Easier to make each variable public
 */
public class Request implements Serializable {
    static final long serialVersionUID=1L;
    // Your data here

    private int _propNum;
    private Object  _propVal;
    private int _seqNum;

    // Your constructor and methods here
    public Request(int propNum, Object propVal, int seqNum)
    {
        this._propNum = propNum;
        this._propVal = propVal;
        this._seqNum  = seqNum;
    }

    // Getter
    public int getPropNum() {return this._propNum;}
    public Object getPropVal() {return this._propVal;}
    public int getSeqNum() {return this._seqNum;}
    
    // Setter
    public void setPropNum(int propNum) {this._propNum = propNum;}
    public void setPropVal(Object propVal) {this._propVal = propVal;}
    public void setSeqNum(int seqNum) {this._seqNum = seqNum;}
}
