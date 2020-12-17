package communication;

import java.security.PublicKey;

public class Interest {
	
	private PublicKey id;
	private int type;
	
	public Interest(PublicKey id, int type) {
		this.id = id;
		this.type = type;
	}
	
	public int getType() {
		return this.type;
	}
	
	public PublicKey getTarget() {
		return this.id;
	}
}
