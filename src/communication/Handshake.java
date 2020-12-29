package communication;

import agents.Participant;

public class Handshake {
	public static final int SYN = 0;
	public static final int SYN_ACK = 1;
	public static final int ACK = 2;
	
	private int sentAt;
	private int type;
	private Participant peer;
	
	/*
	 * Handshake messages using while connection is established
	 */
	
	public Handshake(int sentAt, int type, Participant peer) {
		super();
		this.sentAt = sentAt; //tick when it was sent
		this.type = type; //0 1 or 2
		this.peer = peer; //who sent the handshake msg
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
