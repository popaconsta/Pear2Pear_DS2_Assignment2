package communication;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.List;

import agents.Participant;
import repast.simphony.context.Context;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.query.space.grid.GridCell;
import repast.simphony.query.space.grid.GridCellNgh;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.SpatialMath;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.space.grid.Grid;
import repast.simphony.space.grid.GridPoint;
import repast.simphony.util.ContextUtils;
import repast.simphony.util.SimUtilities;

/*
 * This class abstracts the "event" cited in the paper.
 */
public class Event {
	private PublicKey id; //publicKey of the creator
	private Integer previous; //hash of the previous event in the log including the signature, null if none
	private Integer index; //sequence number, is the position of the event in the log
	private Object content; //defined by applications
	private byte[] signature; //the cryptographic signature of id, previous, index, and content, 
					          //obtained with the privateKey that corresponds to the publicKey
	
	public Event(PublicKey id, Integer previous, Integer index, Object content, PrivateKey privateKey) {
		this.id = id;
		this.previous = previous;
		this.index = index;
		this.content = content;
		this.signature = computeSignature(privateKey);
	}
	
	private byte[] computeSignature(PrivateKey privateKey) {
		byte[] digest = computeDigest();
		byte[] signature = null;
	    
	    try {
	    	Signature sign = Signature.getInstance("NONEwithRSA");
			sign.initSign(privateKey);
			// Adding data to the signature
		    sign.update(digest);
		    // Calculating the signature
		    signature = sign.sign();
		} catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	    return signature;
	}
	
	public byte[] computeDigest() {
		//transform event to stream of bytes
		ByteArrayOutputStream eventStream = new ByteArrayOutputStream();
		try {
			eventStream.write(id.toString().getBytes());
			eventStream.write(previous.toString().getBytes());
			eventStream.write(index.toString().getBytes());
			eventStream.write(content.toString().getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}

		//compute the SHA-256 message digest 
		MessageDigest md;
		byte[] digest = null;
		try {
			md = MessageDigest.getInstance("SHA-256");
			digest = md.digest(eventStream.toByteArray());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		
		return digest;
	}
	
	public boolean isSignatureVerified() {
		byte[] digest = computeDigest();
	    boolean verified = false;
		
	    try {
	    	Signature sign = Signature.getInstance("NONEwithRSA");
			sign.initVerify(this.id);
			// Adding data to the signature
		    sign.update(digest);
		    // Calculating the signature
		    verified = sign.verify(this.signature);
		} catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	    return verified;
	}

	public PublicKey getId() {
		return id;
	}

	public void setId(PublicKey id) {
		this.id = id;
	}

	public Integer getPrevious() {
		return previous;
	}

	public void setPrevious(Integer previous) {
		this.previous = previous;
	}

	public Integer getIndex() {
		return index;
	}

	public void setIndex(Integer index) {
		this.index = index;
	}

	public Object getContent() {
		return content;
	}

	public void setContent(Object content) {
		this.content = content;
	}

	public byte[] getSignature() {
		return signature;
	}

	public void setSignature(byte[] signature) {
		this.signature = signature;
	}
	
	
	
//	@Override
//	public boolean equals(Object obj) {
//		if(obj == null)
//			return false;
//		
//		if(!(obj instanceof Event))
//			return false;
//		
//		Event p = (Event)obj;
//		if(this.source == p.source && this.reference == p.reference && this.type == p.getType()) 
//			return true;
//		else
//			return false;
//	}
	

}
