package communication;

import java.security.PublicKey;

public class Interest {
	
	public static enum Type {
		FOLLOW, UNFOLLOW, BLOCK, UNBLOCK;
	}
	
	private PublicKey targetId;
	private Type type;
	
	
	
	public Interest(PublicKey id, Type type) {
		this.targetId = id;
		this.type = type;
	}
	
	public Type getType() {
		return this.type;
	}
	
	public PublicKey getTargetId() {
		return this.targetId;
	}
}
