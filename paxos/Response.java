// (C) 2020 Bagus Hanindhito (bh29293), Qinzhe Wu (qw2699)

package paxos;
import java.io.Serializable;

/**
 * Please fill in the data structure you use to represent the response message for each RMI call.
 * Hint: You may need a boolean variable to indicate ack of acceptors and also you may need proposal number and value.
 * Hint: Make it more generic such that you can use it for each RMI call.
 */
public class Response implements Serializable {
    static final long serialVersionUID=2L;
    // your data here
    private boolean _status;
    private int _propNum;
    private Object  _propVal;
    private int _seqNum;
    private int _maxSeqNum;

    // Your constructor and methods here

    public Response(boolean status, int propNum, Object propVal, int seqNum, int maxSeqNum)
    {
        this._status = status;
        this._propNum = propNum;
        this._propVal = propVal;
        this._seqNum = seqNum;
        this._maxSeqNum = maxSeqNum;
    }

    // Getter
    public boolean getStatus() {return this._status;}
    public int getPropNum() {return this._propNum;}
    public Object getPropVal() {return this._propVal;}
    public int getSeqNum() {return this._seqNum;}
    public int getMaxSeqNum() {return this._maxSeqNum;}
    
    // Setter
    public void setStatus(boolean status) {this._status = status;}
    public void setPropNum(int propNum) {this._propNum = propNum;}
    public void setPropVal(Object propVal) {this._propVal = propVal;}
    public void setSeqNum(int seqNum) {this._seqNum = seqNum;}
    public void setMaxSeqNum(int maxSeqNum) {this._maxSeqNum = maxSeqNum;}
}
