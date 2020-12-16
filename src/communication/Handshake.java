package communication;

import agents.Participant;

public class Handshake {
	public static final int SYN = 0;
	public static final int SYN_ACK = 1;
	public static final int ACK = 2;
	
	private int sentAt;
	private int type;
	private Participant peer;
	
	public Handshake(int sentAt, int type, Participant peer) {
		super();
		this.sentAt = sentAt;
		this.type = type;
		this.peer = peer;
	}

	public int getSentAt() {
		return sentAt;
	}

	public void setSentAt(int sentAt) {
		this.sentAt = sentAt;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public Participant getPeer() {
		return peer;
	}

	public void setPeer(Participant peer) {
		this.peer = peer;
	}
	
}
