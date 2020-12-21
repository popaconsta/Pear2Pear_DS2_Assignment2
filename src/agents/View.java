package agents;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;


/*
 * This class holds a partial view of the latest participants
 * that have exchanged information with a given participant
 */
public class View extends ArrayList<Participant> {
	
	private String ownerLabel;
    private int maxSize;
    private Map<PublicKey, Integer> lastSeen;

    public View(String ownerLabel, int size){
    	this.ownerLabel = ownerLabel;
        this.maxSize = size;
        this.lastSeen = new HashMap<>();
    }

    public boolean add(Participant p, Integer currentTick){
    	if(p == null)
    		System.out.println("tried to add null");
    	//avoid adding same participant twice in the view
    	int index = -1;
    	for(int i=0; i<size() && index == -1; i++) 
    		if(get(i).equals(p)) 
    			index = i;
    	
    	if(index != -1)
    		remove(index);
    	
    	boolean r = super.add(p);
    	lastSeen.put(p.getId(), currentTick);
        if (size() > maxSize){
            removeRange(0, size() - maxSize);
        }
        
        return r;
    }

    public Participant getYoungest() {
        return get(size() - 1);
    }

    public Participant getOldest() {
        return get(0);
    }
    
    public void updateLastSeen(PublicKey id, Integer currentTick) {
    	//System.out.println("updating " + currentTick);
    	lastSeen.put(id, currentTick);
    }
    
    public void cleanUp(Integer currentTick, String viewOwner) {
    	Iterator<Participant> itr = this.iterator();
    	while(itr.hasNext()){
    	    Participant p = itr.next();
    	    
    	    
    	    if(lastSeen.get(p.getId()) == null || lastSeen.get(p.getId()) + 200 < currentTick) {
    	    	System.out.println(viewOwner + " removing " + p.getLabel() + " " + lastSeen.get(p.getId()));
    	    	itr.remove();  
    	    }	
    	}		 
    }
    
    public Participant getRandomPeer() {
    	if(size() == 0) {
    		System.out.println("Participant(" + ownerLabel + "): empty view, peer sampling aborted.");
    		return null;
    	}
    	
    	int randomIndex = ThreadLocalRandom.current().nextInt(0, size());
    	return get(randomIndex);
    }
}