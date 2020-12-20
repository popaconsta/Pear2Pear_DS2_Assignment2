package agents;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;


/*
 * This class holds a partial view of the latest participants
 * that have exchanged information with a given participant
 */
public class View extends ArrayList<Participant> {

    private int maxSize;

    public View(int size){
        this.maxSize = size;
    }

    public boolean add(Participant p){
    	if(p == null)
    		System.out.println("tried to add nul");
    	//avoid adding same participant twice in the view
    	int index = -1;
    	for(int i=0; i<size() && index == -1; i++) 
    		if(get(i).equals(p)) 
    			index = i;
    	
    	if(index != -1)
    		remove(index);
    	
    	boolean r = super.add(p);
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
    
    public Participant getRandomPeer() {
    	int randomIndex = ThreadLocalRandom.current().nextInt(0, size());
    	return get(randomIndex);
    }
}